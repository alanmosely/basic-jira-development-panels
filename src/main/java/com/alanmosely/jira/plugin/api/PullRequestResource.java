package com.alanmosely.jira.plugin.api;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.rest.common.security.UnrestrictedAccess;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;

@Named
@Path("/code")
public class PullRequestResource {

    private static final Logger log = LoggerFactory.getLogger(PullRequestResource.class);

    private final PullRequestService pullRequestService;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public PullRequestResource(PullRequestService pullRequestService,
            @ComponentImport TransactionTemplate transactionTemplate) {
        this.pullRequestService = pullRequestService;
        this.transactionTemplate = transactionTemplate;
    }

    @POST
    @Path("/{issueKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @UnrestrictedAccess
    public Response addPullRequest(@PathParam("issueKey") String issueKey, final PullRequestModel model) {
        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        log.info("Received POST request to /code/{} from user {}", issueKey, user.getUsername());

        if (issueKey == null || model == null) {
            log.warn("Bad request: issueKey or model is null");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            log.debug("Starting transaction for issueKey: {}", issueKey);
            transactionTemplate.execute((TransactionCallback<Void>) () -> {
                pullRequestService.createPullRequest(issueKey, model);
                log.info("Pull request created for issueKey: {}", issueKey);
                return null;
            });
            log.debug("Transaction completed for issueKey: {}", issueKey);
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            log.error("Error while creating pull request for issueKey: {}", issueKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
