package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.util.SafeUrls;
import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.mail.queue.SingleMailQueueItem;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.opensymphony.module.propertyset.PropertySet;

import net.java.ao.Query;

@Named
public class PullRequestServiceImpl implements PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestServiceImpl.class);

    private static final Comparator<PullRequestEntity> MOST_RECENTLY_UPDATED_FIRST = Comparator
            .comparing(PullRequestEntity::getUpdated, Comparator.nullsFirst(Comparator.<Date>naturalOrder()))
            .reversed();

    private final ActiveObjects activeObjects;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public PullRequestServiceImpl(@ComponentImport ActiveObjects activeObjects,
            @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            @ComponentImport TransactionTemplate transactionTemplate) {
        this.activeObjects = activeObjects;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void createPullRequest(String issueKey, PullRequestModel model) {
        log.debug("Creating pull request for issueKey: {}, model: {}", issueKey, model);
        createPullRequest(resolveIssue(issueKey), issueKey, model);
    }

    @Override
    public void createPullRequest(Issue issue, PullRequestModel model) {
        Objects.requireNonNull(issue, "issue");
        createPullRequest(issue, issue.getKey(), model);
    }

    private void createPullRequest(Issue issue, String requestedKey, PullRequestModel model) {
        String canonicalKey = issue != null ? issue.getKey() : requestedKey;
        String url = StringUtils.trimToNull(model.getUrl());

        // Save first, notify after: the mail queue is not transactional, so emails
        // enqueued inside the transaction would still go out on rollback.
        PullRequestEntity entity = transactionTemplate.execute((TransactionCallback<PullRequestEntity>) () -> {
            PullRequestEntity found = findOrCreateEntity(issue, canonicalKey, url);
            // Always store the canonical key: upserts onto legacy rows would otherwise
            // keep a stale pre-rename key forever.
            found.setIssueKey(canonicalKey);
            updateEntityFromModel(found, model);
            found.save();
            return found;
        });
        log.info("Pull request entity saved for issueKey: {}", canonicalKey);

        // Everything from here on is notification handling; the save has committed,
        // so no failure below (including the settings read) may escape as an error.
        try {
            if (areNotificationsEnabled()) {
                sendNotifications(entity);
            } else {
                log.info("Pull request processing is disabled by administrator.");
            }
        } catch (RuntimeException e) {
            log.error("Error sending notifications for issueKey: {}", canonicalKey, e);
        }
    }

    @Override
    public boolean deletePullRequest(Issue issue, String url) {
        String targetUrl = StringUtils.trimToNull(url);
        if (issue == null || targetUrl == null) {
            return false;
        }

        Boolean deleted = transactionTemplate.execute((TransactionCallback<Boolean>) () -> {
            // Deliberately not deduplicated: racing upserts can leave duplicate rows
            // for the same URL, and a delete must remove all of them.
            List<PullRequestEntity> matches = new ArrayList<>();
            for (PullRequestEntity entity : findPullRequestEntities(issue, issue.getKey())) {
                if (StringUtils.equals(StringUtils.trim(entity.getUrl()), targetUrl)) {
                    matches.add(entity);
                }
            }
            if (matches.isEmpty()) {
                return false;
            }
            activeObjects.delete(matches.toArray(new PullRequestEntity[0]));
            return true;
        });
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Deleted pull request entries for issueKey: {}", issue.getKey());
            return true;
        }
        return false;
    }

    private boolean areNotificationsEnabled() {
        String value = (String) pluginSettingsFactory.createGlobalSettings()
                .get(SettingsKeys.NOTIFICATIONS_ENABLED_KEY);
        return value == null || Boolean.parseBoolean(value);
    }

    void sendNotifications(PullRequestEntity entity) {
        log.debug("Processing pull request entity for issueKey: {}", entity.getIssueKey());

        Issue issue = resolveIssue(entity.getIssueKey());
        if (issue == null) {
            log.warn("Issue not found for issueKey: {}", entity.getIssueKey());
            return;
        }

        Set<ApplicationUser> involvedUsers = new HashSet<>();
        if (issue.getAssignee() != null) {
            involvedUsers.add(issue.getAssignee());
        }
        if (issue.getReporter() != null) {
            involvedUsers.add(issue.getReporter());
        }
        involvedUsers.addAll(watchers(issue));

        int queued = 0;
        for (ApplicationUser user : involvedUsers) {
            if (user == null) {
                continue;
            }
            if (isOptedIn(user) && sendEmailToUser(user, issue, entity)) {
                queued++;
            }
        }
        if (queued > 0) {
            log.info("Queued {} notification email(s) for issue {}", queued, issue.getKey());
        }
    }

    protected Collection<ApplicationUser> watchers(Issue issue) {
        return ComponentAccessor.getWatcherManager().getWatchersUnsorted(issue);
    }

    protected boolean isOptedIn(ApplicationUser user) {
        PropertySet userProperties = ComponentAccessor.getUserPropertyManager().getPropertySet(user);
        return userProperties.getBoolean(SettingsKeys.CODE_NOTIFICATIONS_USER_PROPERTY);
    }

    protected boolean sendEmailToUser(ApplicationUser user, Issue issue, PullRequestEntity entity) {
        log.debug("Sending email to user: {} for issueKey: {}", user.getUsername(), issue.getKey());

        try {
            String issueUrl = ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL)
                    + "/browse/" + issue.getKey();

            Email email = new Email(user.getEmailAddress());
            email.setSubject(buildEmailSubject(issue, entity));
            email.setBody(buildEmailBody(issueUrl, issue, entity));
            email.setMimeType("text/html");

            SingleMailQueueItem mailItem = new SingleMailQueueItem(email);
            ComponentAccessor.getMailQueue().addItem(mailItem);
            log.debug("Email queued for user: {}", user.getUsername());
            return true;
        } catch (Exception e) {
            log.error("Error queueing email to user: {}", user.getUsername(), e);
            return false;
        }
    }

    static String buildEmailSubject(Issue issue, PullRequestEntity entity) {
        // defaultString: optional fields must render empty, not as the literal "null".
        return sanitizeHeader("("
                + issue.getKey() + ": "
                + StringUtils.defaultString(issue.getSummary()) + ") ["
                + StringUtils.defaultString(entity.getStatus()) + "] "
                + StringUtils.defaultString(entity.getRepoName()) + " - "
                + StringUtils.defaultString(entity.getName()));
    }

    // All entity fields originate from the REST API and are attacker-influenced:
    // escape them before embedding in HTML, and only link URLs with a safe scheme.
    static String buildEmailBody(String issueUrl, Issue issue, PullRequestEntity entity) {
        String issueTitle = escapeHtml(issue.getSummary());
        String prName = escapeHtml(entity.getName());
        String repoName = escapeHtml(entity.getRepoName());
        String branchName = escapeHtml(entity.getBranchName());
        String status = escapeHtml(entity.getStatus());

        return "<html>"
                + "<body>"
                + "<p>There has been a code update related to "
                + "<strong><a href=\"" + escapeHtml(issueUrl) + "\">" + escapeHtml(issue.getKey())
                + "</a></strong> (" + issueTitle
                + ").</p>"
                + "<br/><p><strong>Details:</strong></p>"
                + "<ul>"
                + "<li><strong>Pull Request:</strong> " + linkOrText(entity.getUrl(), prName) + "</li>"
                + "<li><strong>Repository:</strong> " + linkOrText(entity.getRepoUrl(), repoName) + "</li>"
                + "<li><strong>Branch:</strong> " + branchName + "</li>"
                + "<li><strong>Status:</strong> " + status + "</li>"
                + "</ul>"
                + "</body>"
                + "</html>";
    }

    /**
     * Minimal HTML escaper covering &amp;, &lt;, &gt;, double quote and apostrophe.
     * Unlike commons-lang's (deprecated) escapeHtml4 it escapes apostrophes too, so
     * escaped values are safe in single- or double-quoted attributes; attributes in
     * the email body stay double-quoted by convention regardless.
     */
    static String escapeHtml(String value) {
        String source = StringUtils.defaultString(value);
        StringBuilder escaped = new StringBuilder(source.length() + 16);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }

    private static String sanitizeHeader(String value) {
        return StringUtils.defaultString(value).replaceAll("[\\r\\n]+", " ");
    }

    static String linkOrText(String url, String escapedText) {
        if (SafeUrls.isHttpUrl(url)) {
            return "<a href=\"" + escapeHtml(url) + "\">" + escapedText + "</a>";
        }
        return escapedText;
    }

    private PullRequestEntity findOrCreateEntity(Issue issue, String issueKey, String url) {
        log.debug("Finding or creating PullRequestEntity for issueKey: {}, url: {}", issueKey, url);

        // A URL-less payload can never be an upsert: dedupeByUrl deliberately treats
        // every URL-less row as unique, so matching null against null here would
        // collapse — and delete — rows the display keeps (deletePullRequest guards
        // the same way). Unreachable via REST, which validates url as http(s).
        if (url == null) {
            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            if (issue != null) {
                entity.setIssueId(issue.getId());
            }
            return entity;
        }

        if (issue != null) {
            // ONE candidate pool spanning both buckets — the issue's ISSUE_ID rows and
            // the pre-ISSUE_ID legacy rows — matched trimmed in Java rather than
            // exact-match in SQL (rows written before trim-on-store may hold padded
            // URLs, and legacy rows must be updated, not duplicated). A staged search
            // would let a newer row in the unsearched bucket shadow the upsert target.
            // Row counts per issue are small.
            List<PullRequestEntity> pool = new ArrayList<>();
            Collections.addAll(pool, activeObjects.find(PullRequestEntity.class,
                    Query.select().where("ISSUE_ID = ?", issue.getId())));
            Collections.addAll(pool, findLegacyEntities(issue, issue.getKey()));

            PullRequestEntity match = newestMatchDeletingDuplicates(pool, url);
            if (match != null) {
                backfillIssueId(match, issue.getId());
                return match;
            }

            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueId(issue.getId());
            return entity;
        }

        List<PullRequestEntity> byKey = new ArrayList<>();
        Collections.addAll(byKey, activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ?", issueKey)));
        PullRequestEntity match = newestMatchDeletingDuplicates(byKey, url);
        if (match != null) {
            return match;
        }

        // The caller sets the issue key on whatever this returns.
        return activeObjects.create(PullRequestEntity.class);
    }

    /**
     * Picks the upsert target among rows matching the (trimmed) URL. Racing upserts
     * can have left duplicates; the newest row wins — the same rule the display's
     * dedupeByUrl uses, so the row an upsert writes to is always the row shown —
     * and the losers are deleted inside the same transaction, self-healing the
     * table. 'Updated' is client-controllable, so agreeing on one row matters:
     * otherwise an upsert could keep writing to a row that a stale duplicate with
     * a later timestamp shadows forever.
     */
    private PullRequestEntity newestMatchDeletingDuplicates(List<PullRequestEntity> candidates, String url) {
        List<PullRequestEntity> matches = new ArrayList<>();
        for (PullRequestEntity candidate : candidates) {
            if (StringUtils.equals(StringUtils.trim(candidate.getUrl()), url)) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(MOST_RECENTLY_UPDATED_FIRST);
        if (matches.size() > 1) {
            List<PullRequestEntity> losers = matches.subList(1, matches.size());
            activeObjects.delete(losers.toArray(new PullRequestEntity[0]));
            log.info("Removed {} duplicate pull request row(s) during upsert", losers.size());
        }
        return matches.get(0);
    }

    private void updateEntityFromModel(PullRequestEntity entity, PullRequestModel model) {
        // trimToNull: a blank optional field is the same absence as an omitted one,
        // so both must store (and later serialize) as null, never as "".
        entity.setName(StringUtils.trimToNull(model.getName()));
        entity.setUrl(StringUtils.trimToNull(model.getUrl()));
        entity.setStatus(StringUtils.trimToNull(model.getStatus()));
        entity.setRepoName(StringUtils.trimToNull(model.getRepoName()));
        entity.setRepoUrl(StringUtils.trimToNull(model.getRepoUrl()));
        entity.setBranchName(StringUtils.trimToNull(model.getBranchName()));
        // Honor the client's timestamp when it sends one (PR platforms know the real
        // update time); fall back to arrival time.
        entity.setUpdated(model.getUpdated() != null ? model.getUpdated() : new Date());
    }

    // The read methods propagate persistence failures: the panels catch and render
    // empty themselves, while the REST GET must be able to answer 500 rather than
    // a misleading 200 with an empty list.

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        log.debug("Getting pull requests for issueKey: {}", issueKey);
        return mapEntitiesToModels(dedupeByUrl(findPullRequestEntities(resolveIssue(issueKey), issueKey)));
    }

    @Override
    public List<PullRequestModel> getPullRequests(Issue issue) {
        return mapEntitiesToModels(dedupeByUrl(findPullRequestEntities(issue, issue.getKey())));
    }

    @Override
    public boolean hasPullRequests(String issueKey) {
        log.debug("Checking if issueKey {} has pull requests", issueKey);

        try {
            return countPullRequestEntities(resolveIssue(issueKey), issueKey) > 0;
        } catch (Exception e) {
            log.error("Error checking pull requests for issueKey: {}", issueKey, e);
            return false;
        }
    }

    @Override
    public boolean hasPullRequests(Issue issue) {
        try {
            return countPullRequestEntities(issue, issue.getKey()) > 0;
        } catch (Exception e) {
            log.error("Error checking pull requests for issue: {}", issue.getKey(), e);
            return false;
        }
    }

    private List<PullRequestModel> mapEntitiesToModels(PullRequestEntity[] entities) {
        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            models.add(mapEntityToModel(entity));
        }
        return models;
    }

    private PullRequestModel mapEntityToModel(PullRequestEntity entity) {
        PullRequestModel model = new PullRequestModel();

        model.setName(entity.getName());
        // Rows written before URL validation existed may hold unsafe schemes
        // (javascript:, data:); never hand those to the templates as link targets.
        model.setUrl(SafeUrls.isHttpUrl(entity.getUrl()) ? entity.getUrl() : null);
        model.setStatus(entity.getStatus());
        model.setRepoName(entity.getRepoName());
        model.setRepoUrl(SafeUrls.isHttpUrl(entity.getRepoUrl()) ? entity.getRepoUrl() : null);
        model.setBranchName(entity.getBranchName());
        model.setUpdated(entity.getUpdated());

        return model;
    }

    /**
     * Concurrent POSTs for the same issue + URL can race findOrCreateEntity and
     * leave duplicate rows (AO cannot express a composite unique constraint), so
     * display keeps only the newest row per URL. Older duplicates stay in the
     * table and are still matched by upserts and deletes.
     */
    private static PullRequestEntity[] dedupeByUrl(PullRequestEntity[] sortedNewestFirst) {
        Set<String> seenUrls = new HashSet<>();
        List<PullRequestEntity> unique = new ArrayList<>(sortedNewestFirst.length);
        for (PullRequestEntity entity : sortedNewestFirst) {
            String url = StringUtils.trimToNull(entity.getUrl());
            if (url == null || seenUrls.add(url)) {
                unique.add(entity);
            }
        }
        return unique.toArray(new PullRequestEntity[0]);
    }

    private PullRequestEntity[] findPullRequestEntities(Issue issue, String issueKey) {
        if (issue == null) {
            // The key does not resolve (e.g. the issue was deleted): fall back to
            // stored-key match. Rows carrying a stale non-null ISSUE_ID under a
            // re-used key are deliberately not matched elsewhere — they belong to
            // a different, since-deleted issue.
            PullRequestEntity[] byKey = activeObjects.find(
                    PullRequestEntity.class,
                    Query.select().where("ISSUE_KEY = ?", issueKey));
            List<PullRequestEntity> sorted = new ArrayList<>(List.of(byKey));
            sorted.sort(MOST_RECENTLY_UPDATED_FIRST);
            return sorted.toArray(new PullRequestEntity[0]);
        }

        List<PullRequestEntity> combined = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        PullRequestEntity[] byId = activeObjects.find(
                PullRequestEntity.class,
                Query.select().where("ISSUE_ID = ?", issue.getId()));
        for (PullRequestEntity entity : byId) {
            combined.add(entity);
            seenIds.add(entity.getID());
        }

        for (PullRequestEntity entity : findLegacyEntities(issue, issueKey)) {
            backfillIssueId(entity, issue.getId());
            if (seenIds.add(entity.getID())) {
                combined.add(entity);
            }
        }

        combined.sort(MOST_RECENTLY_UPDATED_FIRST);
        return combined.toArray(new PullRequestEntity[0]);
    }

    /**
     * Runs on every issue view via panel conditions, so it must stay on indexed
     * queries only: unlike the read path it deliberately skips the un-indexable
     * UPPER(ISSUE_KEY) rescue (a full table scan). Legacy rows whose stored key
     * matches only case-insensitively stay hidden until a read or upsert backfills
     * their ISSUE_ID — an accepted trade-off, see AGENTS.md.
     */
    private int countPullRequestEntities(Issue issue, String issueKey) {
        if (issue == null) {
            return activeObjects.count(PullRequestEntity.class, Query.select()
                    .where("ISSUE_KEY = ?", issueKey));
        }

        int count = activeObjects.count(PullRequestEntity.class, Query.select()
                .where("ISSUE_ID = ?", issue.getId()));
        if (count > 0) {
            return count;
        }

        List<String> keys = legacyKeyCandidates(issue, issueKey);
        return activeObjects.count(PullRequestEntity.class,
                Query.select().where(legacyWhere(keys, false), keys.toArray()));
    }

    /**
     * Finds rows created before the ISSUE_ID column existed (they only carry an issue
     * key). The issue's full key history covers project key renames, so a row stored
     * under a pre-rename key is still found when the issue is viewed under its current
     * key. The exact-match query is index-backed; the case-insensitive variant only
     * runs as a rescue when the exact match finds nothing.
     */
    private PullRequestEntity[] findLegacyEntities(Issue issue, String requestedKey) {
        List<String> keys = legacyKeyCandidates(issue, requestedKey);

        PullRequestEntity[] exact = activeObjects.find(
                PullRequestEntity.class,
                Query.select().where(legacyWhere(keys, false), keys.toArray()));
        if (exact.length > 0) {
            return exact;
        }

        return activeObjects.find(
                PullRequestEntity.class,
                Query.select().where(legacyWhere(keys, true), upperCased(keys).toArray()));
    }

    private List<String> legacyKeyCandidates(Issue issue, String requestedKey) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(issue.getKey());
        if (StringUtils.isNotBlank(requestedKey)) {
            keys.add(requestedKey);
        }
        for (String key : resolveAllIssueKeys(issue)) {
            if (StringUtils.isNotBlank(key)) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    private static String legacyWhere(List<String> keys, boolean caseInsensitive) {
        String placeholders = String.join(", ", Collections.nCopies(keys.size(), "?"));
        String column = caseInsensitive ? "UPPER(ISSUE_KEY)" : "ISSUE_KEY";
        return "ISSUE_ID IS NULL AND " + column + " IN (" + placeholders + ")";
    }

    private static List<String> upperCased(List<String> keys) {
        List<String> upper = new ArrayList<>(keys.size());
        for (String key : keys) {
            upper.add(key.toUpperCase(Locale.ROOT));
        }
        return upper;
    }

    private void backfillIssueId(PullRequestEntity entity, Long issueId) {
        if (entity.getIssueId() != null) {
            return;
        }
        try {
            entity.setIssueId(issueId);
            entity.save();
            log.debug("Backfilled issueId {} for pull request entity with issueKey {}", issueId,
                    entity.getIssueKey());
        } catch (Exception e) {
            // Backfill runs on read paths; a failed write must not break rendering.
            log.warn("Unable to backfill issueId {} for pull request entity {}", issueId, entity.getID(), e);
        }
    }

    protected Issue resolveIssue(String issueKey) {
        try {
            if (ComponentAccessor.getIssueManager() == null) {
                return null;
            }
            return ComponentAccessor.getIssueManager().getIssueObject(issueKey);
        } catch (Exception e) {
            log.debug("Unable to resolve issue for issueKey {}", issueKey, e);
            return null;
        }
    }

    protected Set<String> resolveAllIssueKeys(Issue issue) {
        try {
            Set<String> keys = ComponentAccessor.getIssueManager().getAllIssueKeys(issue.getId());
            return keys != null ? keys : Collections.emptySet();
        } catch (Exception e) {
            log.debug("Unable to resolve historical keys for issue {}", issue.getKey(), e);
            return Collections.emptySet();
        }
    }
}
