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
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.util.SafeUrls;
import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
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

        Issue issue = resolveIssue(issueKey);
        String canonicalKey = issue != null ? issue.getKey() : issueKey;

        // Save first, notify after: the mail queue is not transactional, so emails
        // enqueued inside the transaction would still go out on rollback.
        PullRequestEntity entity = transactionTemplate.execute((TransactionCallback<PullRequestEntity>) () -> {
            PullRequestEntity found = findOrCreateEntity(issue, canonicalKey, model.getUrl());
            updateEntityFromModel(found, model);
            found.save();
            return found;
        });
        log.info("Pull request entity saved for issueKey: {}", canonicalKey);

        if (areNotificationsEnabled()) {
            try {
                sendNotifications(entity);
            } catch (RuntimeException e) {
                log.error("Error sending notifications for issueKey: {}", canonicalKey, e);
            }
        } else {
            log.info("Pull request processing is disabled by administrator.");
        }
    }

    private boolean areNotificationsEnabled() {
        String value = (String) pluginSettingsFactory.createGlobalSettings()
                .get(SettingsKeys.NOTIFICATIONS_ENABLED_KEY);
        return value == null || Boolean.parseBoolean(value);
    }

    void sendNotifications(PullRequestEntity entity) {
        log.debug("Processing pull request entity for issueKey: {}", entity.getIssueKey());

        Set<ApplicationUser> involvedUsers = new HashSet<>();
        Issue issue = ComponentAccessor.getIssueManager().getIssueObject(entity.getIssueKey());

        if (issue == null) {
            log.warn("Issue not found for issueKey: {}", entity.getIssueKey());
            return;
        }

        if (issue.getAssignee() != null) {
            involvedUsers.add(issue.getAssignee());
        }

        if (issue.getReporter() != null) {
            involvedUsers.add(issue.getReporter());
        }

        WatcherManager watcherManager = ComponentAccessor.getWatcherManager();
        Collection<ApplicationUser> watchers = watcherManager.getWatchersUnsorted(issue);
        involvedUsers.addAll(watchers);

        UserPropertyManager userPropertyManager = ComponentAccessor.getUserPropertyManager();

        for (ApplicationUser user : involvedUsers) {
            if (user == null) {
                continue;
            }

            PropertySet userProperties = userPropertyManager.getPropertySet(user);
            boolean codeNotifications = userProperties.getBoolean("com.alanmosely.jira.plugin.codeNotifications");

            if (codeNotifications) {
                sendEmailToUser(user, issue, entity);
                log.info("Sent email to user: {}", user.getUsername());
            }
        }
    }

    private void sendEmailToUser(ApplicationUser user, Issue issue, PullRequestEntity entity) {
        log.debug("Sending email to user: {} for issueKey: {}", user.getUsername(), issue.getKey());

        try {
            String issueUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl")
                    + "/browse/" + issue.getKey();

            // All entity fields originate from the REST API and are attacker-influenced:
            // escape them before embedding in HTML, and only link URLs with a safe scheme.
            String issueTitle = escapeHtml(issue.getSummary());
            String prName = escapeHtml(entity.getName());
            String repoName = escapeHtml(entity.getRepoName());
            String branchName = escapeHtml(entity.getBranchName());
            String status = escapeHtml(entity.getStatus());

            String emailSubject = sanitizeHeader("("
                    + issue.getKey() + ": "
                    + issue.getSummary() + ") ["
                    + entity.getStatus() + "] "
                    + entity.getRepoName() + " - "
                    + entity.getName());

            Email email = new Email(user.getEmailAddress());
            email.setSubject(emailSubject);

            String emailBody = "<html>"
                    + "<body>"
                    + "<p>There has been a code update related to "
                    + "<strong><a href=\"" + issueUrl + "\">" + issue.getKey() + "</a></strong> (" + issueTitle
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
            email.setBody(emailBody);
            email.setMimeType("text/html");

            SingleMailQueueItem mailItem = new SingleMailQueueItem(email);
            ComponentAccessor.getMailQueue().addItem(mailItem);
            log.debug("Email queued for user: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Error sending email to user: {}", user.getUsername(), e);
        }
    }

    private static String escapeHtml(String value) {
        // escapeHtml4 does not escape apostrophes, so HTML attributes built with
        // escaped values must be double-quoted.
        return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value));
    }

    private static String sanitizeHeader(String value) {
        return StringUtils.defaultString(value).replaceAll("[\\r\\n]+", " ");
    }

    private static String linkOrText(String url, String escapedText) {
        if (SafeUrls.isHttpUrl(url)) {
            return "<a href=\"" + escapeHtml(url) + "\">" + escapedText + "</a>";
        }
        return escapedText;
    }

    private PullRequestEntity findOrCreateEntity(Issue issue, String issueKey, String url) {
        log.debug("Finding or creating PullRequestEntity for issueKey: {}, url: {}", issueKey, url);

        if (issue != null) {
            PullRequestEntity[] byId = activeObjects.find(PullRequestEntity.class,
                    Query.select().where("ISSUE_ID = ? AND URL = ?", issue.getId(), url));
            if (byId.length > 0) {
                return byId[0];
            }

            // A row written before the ISSUE_ID column existed must be updated,
            // not duplicated, when the same PR URL is posted again.
            for (PullRequestEntity legacy : findLegacyEntities(issue, issueKey)) {
                if (StringUtils.equals(legacy.getUrl(), url)) {
                    backfillIssueId(legacy, issue.getId());
                    return legacy;
                }
            }

            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueKey(issueKey);
            entity.setIssueId(issue.getId());
            return entity;
        }

        PullRequestEntity[] byKey = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ? AND URL = ?", issueKey, url));
        if (byKey.length > 0) {
            return byKey[0];
        }

        PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
        entity.setIssueKey(issueKey);
        return entity;
    }

    private void updateEntityFromModel(PullRequestEntity entity, PullRequestModel model) {
        entity.setName(model.getName());
        entity.setUrl(model.getUrl());
        entity.setStatus(model.getStatus());
        entity.setRepoName(model.getRepoName());
        entity.setRepoUrl(model.getRepoUrl());
        entity.setBranchName(model.getBranchName());
        entity.setUpdated(new Date());
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        log.debug("Getting pull requests for issueKey: {}", issueKey);

        try {
            return mapEntitiesToModels(findPullRequestEntities(resolveIssue(issueKey), issueKey));
        } catch (Exception e) {
            log.error("Error getting pull requests for issueKey: {}", issueKey, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PullRequestModel> getPullRequests(Issue issue) {
        try {
            return mapEntitiesToModels(findPullRequestEntities(issue, issue.getKey()));
        } catch (Exception e) {
            log.error("Error getting pull requests for issue: {}", issue.getKey(), e);
            return new ArrayList<>();
        }
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
        count = activeObjects.count(PullRequestEntity.class,
                Query.select().where(legacyWhere(keys, false), keys.toArray()));
        if (count > 0) {
            return count;
        }
        return activeObjects.count(PullRequestEntity.class,
                Query.select().where(legacyWhere(keys, true), upperCased(keys).toArray()));
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
