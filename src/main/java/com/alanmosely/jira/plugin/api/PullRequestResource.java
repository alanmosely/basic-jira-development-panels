package com.alanmosely.jira.plugin.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.alanmosely.jira.plugin.util.SafeUrls;
import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.rest.api.security.annotation.LicensedOnly;
import com.atlassian.plugins.rest.api.security.annotation.UnrestrictedAccess;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

@Named
@Path("/code")
@Produces(MediaType.APPLICATION_JSON)
public class PullRequestResource {

    private static final Logger log = LoggerFactory.getLogger(PullRequestResource.class);

    /** Matches the AO schema: every String column on PullRequestEntity is VARCHAR(255). */
    static final int MAX_FIELD_LENGTH = 255;

    private final PullRequestService pullRequestService;
    private final PluginSettings pluginSettings;

    @Inject
    public PullRequestResource(PullRequestService pullRequestService,
            @ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pullRequestService = pullRequestService;
        this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
    }

    @POST
    @Path("/{issueKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @LicensedOnly
    public Response addPullRequest(@PathParam("issueKey") String issueKey, final PullRequestModel model) {
        ApplicationUser user = loggedInUser();
        String username = user != null ? user.getUsername() : null;
        log.info("Received POST request to /code/{} from user {}", logSafe(issueKey), username);

        // Writes require an authenticated caller. @LicensedOnly already enforces
        // this at the platform layer; the in-code check keeps the rule visible
        // and unit-testable.
        if (user == null) {
            return error(Response.Status.UNAUTHORIZED, "Authentication is required to create pull requests.");
        }
        if (StringUtils.isBlank(issueKey) || model == null) {
            return badRequest("An issue key and a request body are required.");
        }
        trimModel(model);
        if (StringUtils.isBlank(model.getName())) {
            return badRequest("'name' is required.");
        }
        if (!SafeUrls.isHttpUrl(model.getUrl())) {
            return badRequest("'url' is required and must be an http(s) URL.");
        }
        if (StringUtils.isNotBlank(model.getRepoUrl()) && !SafeUrls.isHttpUrl(model.getRepoUrl())) {
            return badRequest("'repoUrl' must be an http(s) URL.");
        }
        String overLengthField = firstOverLengthField(model);
        if (overLengthField != null) {
            return badRequest("'" + overLengthField + "' must be at most " + MAX_FIELD_LENGTH + " characters.");
        }

        Response forbidden = enforceApiUser(username);
        if (forbidden != null) {
            return forbidden;
        }

        Issue issue = issueByKey(issueKey);
        // Same response for a missing issue and an invisible one, so the endpoint
        // cannot be used to probe which issue keys exist.
        if (issue == null || !canBrowse(issue, user)) {
            log.warn("Rejected pull request for {}: issue missing or not browsable by user {}", logSafe(issueKey),
                    username);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            pullRequestService.createPullRequest(issue, model);
            log.info("Pull request created for issueKey: {}", issue.getKey());
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            log.error("Error while creating pull request for issueKey: {}", logSafe(issueKey), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/{issueKey}")
    @UnrestrictedAccess
    public Response getPullRequests(@PathParam("issueKey") String issueKey) {
        // Reads mirror UI visibility: whoever can browse the issue (including
        // anonymous users, where the project allows it) can read its pull requests.
        ApplicationUser user = loggedInUser();
        Issue issue = issueByKey(issueKey);
        if (issue == null || !canBrowse(issue, user)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            return Response.ok(pullRequestService.getPullRequests(issue)).build();
        } catch (Exception e) {
            // A read failure must be a 500, not a misleading 200 with an empty list.
            log.error("Error while reading pull requests for issueKey: {}", logSafe(issueKey), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DELETE
    @Path("/{issueKey}")
    @LicensedOnly
    public Response deletePullRequest(@PathParam("issueKey") String issueKey, @QueryParam("url") String url) {
        ApplicationUser user = loggedInUser();
        String username = user != null ? user.getUsername() : null;
        log.info("Received DELETE request to /code/{} from user {}", logSafe(issueKey), username);

        if (user == null) {
            return error(Response.Status.UNAUTHORIZED, "Authentication is required to delete pull requests.");
        }
        if (StringUtils.isBlank(url)) {
            return badRequest("A 'url' query parameter identifying the pull request is required.");
        }

        Response forbidden = enforceApiUser(username);
        if (forbidden != null) {
            return forbidden;
        }

        Issue issue = issueByKey(issueKey);
        if (issue == null || !canBrowse(issue, user)) {
            log.warn("Rejected pull request deletion for {}: issue missing or not browsable by user {}",
                    logSafe(issueKey), username);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            if (!pullRequestService.deletePullRequest(issue, url)) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            log.info("Pull request deleted for issueKey: {}", issue.getKey());
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Error while deleting pull request for issueKey: {}", logSafe(issueKey), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Response enforceApiUser(String username) {
        String configuredApiUser = (String) pluginSettings.get(SettingsKeys.API_USER_KEY);
        if (StringUtils.isNotBlank(configuredApiUser) && !sameUsername(username, configuredApiUser.trim())) {
            log.warn("Unauthorized attempt to modify pull requests by user {}", username);
            return error(Response.Status.FORBIDDEN,
                    "Only the configured API user can create or delete pull requests.");
        }
        return null;
    }

    /**
     * Jira treats usernames as case-insensitively unique; compare via Locale.ROOT
     * lower-casing rather than equalsIgnoreCase, which is broader than Jira's own
     * canonicalization (equalsIgnoreCase merges e.g. the Turkish dotless i with 'i',
     * letting a distinct account impersonate the configured API user).
     */
    private static boolean sameUsername(String username, String configured) {
        return username != null
                && username.toLowerCase(Locale.ROOT).equals(configured.toLowerCase(Locale.ROOT));
    }

    private static void trimModel(PullRequestModel model) {
        // trimToNull: a blank field must behave exactly like an omitted one, so the
        // stored value (and GET's JSON) has a single representation of absence.
        model.setName(StringUtils.trimToNull(model.getName()));
        model.setUrl(StringUtils.trimToNull(model.getUrl()));
        model.setStatus(StringUtils.trimToNull(model.getStatus()));
        model.setRepoName(StringUtils.trimToNull(model.getRepoName()));
        model.setRepoUrl(StringUtils.trimToNull(model.getRepoUrl()));
        model.setBranchName(StringUtils.trimToNull(model.getBranchName()));
    }

    /** Path params are attacker-controlled; strip CR/LF so they cannot forge log lines. */
    private static String logSafe(String value) {
        return value == null ? null : value.replace('\r', '_').replace('\n', '_');
    }

    private static String firstOverLengthField(PullRequestModel model) {
        if (tooLong(model.getName())) {
            return "name";
        }
        if (tooLong(model.getUrl())) {
            return "url";
        }
        if (tooLong(model.getStatus())) {
            return "status";
        }
        if (tooLong(model.getRepoName())) {
            return "repoName";
        }
        if (tooLong(model.getRepoUrl())) {
            return "repoUrl";
        }
        if (tooLong(model.getBranchName())) {
            return "branchName";
        }
        return null;
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_FIELD_LENGTH;
    }

    private static Response badRequest(String message) {
        return error(Response.Status.BAD_REQUEST, message);
    }

    /** Error bodies use Jira's REST error shape so integrations can parse them. */
    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("errorMessages", List.of(message))).build();
    }

    // Protected seams so unit tests can supply Jira state without mocking
    // ComponentAccessor statics — same pattern as PullRequestServiceImpl.

    protected ApplicationUser loggedInUser() {
        return ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
    }

    protected Issue issueByKey(String issueKey) {
        return ComponentAccessor.getIssueManager().getIssueObject(issueKey);
    }

    protected boolean canBrowse(Issue issue, ApplicationUser user) {
        return ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue,
                user);
    }
}
