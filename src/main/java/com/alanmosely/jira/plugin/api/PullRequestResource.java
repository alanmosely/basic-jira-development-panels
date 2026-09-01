package com.alanmosely.jira.plugin.api;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
import com.atlassian.plugins.rest.api.security.annotation.UnrestrictedAccess;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

@Named
@Path("/code")
public class PullRequestResource {

    private static final Logger log = LoggerFactory.getLogger(PullRequestResource.class);

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
    @Produces(MediaType.APPLICATION_JSON)
    @UnrestrictedAccess
    public Response addPullRequest(@PathParam("issueKey") String issueKey, final PullRequestModel model) {
        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        String username = user != null ? user.getUsername() : null;
        log.info("Received POST request to /code/{} from user {}", issueKey, username);

        if (StringUtils.isBlank(issueKey) || model == null) {
            return badRequest("An issue key and a request body are required.");
        }
        if (StringUtils.isBlank(model.getName())) {
            return badRequest("'name' is required.");
        }
        if (!SafeUrls.isHttpUrl(model.getUrl())) {
            return badRequest("'url' is required and must be an http(s) URL.");
        }
        if (StringUtils.isNotBlank(model.getRepoUrl()) && !SafeUrls.isHttpUrl(model.getRepoUrl())) {
            return badRequest("'repoUrl' must be an http(s) URL.");
        }

        String configuredApiUser = (String) pluginSettings.get(SettingsKeys.API_USER_KEY);
        if (StringUtils.isNotBlank(configuredApiUser)) {
            // Jira treats usernames as case-insensitively unique, so the comparison
            // matches login semantics rather than the admin's typed casing.
            if (username == null || !username.equalsIgnoreCase(configuredApiUser.trim())) {
                log.warn("Unauthorized attempt to create pull request by user {}", username);
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Only the configured API user can create pull requests.")
                        .build();
            }
        }

        Issue issue = ComponentAccessor.getIssueManager().getIssueObject(issueKey);
        // Same response for a missing issue and an invisible one, so the endpoint
        // cannot be used to probe which issue keys exist.
        if (issue == null || !ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, user)) {
            log.warn("Rejected pull request for {}: issue missing or not browsable by user {}", issueKey, username);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            pullRequestService.createPullRequest(issue.getKey(), model);
            log.info("Pull request created for issueKey: {}", issue.getKey());
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            log.error("Error while creating pull request for issueKey: {}", issueKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
    }
}
