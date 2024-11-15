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

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.rest.common.security.UnrestrictedAccess;
import com.atlassian.sal.api.transaction.TransactionTemplate;

@Named
@Path("/code")
public class PullRequestResource {

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
        if (issueKey == null || model == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        transactionTemplate.execute(() -> {
            pullRequestService.createPullRequest(issueKey, model);
            return null;
        });

        return Response.status(Response.Status.CREATED).build();
    }
}
