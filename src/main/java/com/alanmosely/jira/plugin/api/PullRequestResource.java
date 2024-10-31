package com.alanmosely.jira.plugin.api;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import javax.inject.Inject;
import javax.inject.Named;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Named
@Path("/code")
public class PullRequestResource {

    private final PullRequestService pullRequestService;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public PullRequestResource(PullRequestService pullRequestService, @ComponentImport TransactionTemplate transactionTemplate) {
        this.pullRequestService = pullRequestService;
        this.transactionTemplate = transactionTemplate;
    }

    @POST
    @Path("/{issueKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @AnonymousAllowed
    public Response addPullRequest(@PathParam("issueKey") String issueKey, final PullRequestModel model) {
        if (issueKey == null || model == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                pullRequestService.createPullRequest(issueKey, model);
                return null;
            }
        });

        return Response.status(Response.Status.CREATED).build();
    }
}
