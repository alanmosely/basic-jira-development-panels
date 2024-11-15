package com.alanmosely.jira.plugin.tabpanel;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueTabPanel;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.user.ApplicationUser;

@Named
public class PullRequestTabPanel extends AbstractIssueTabPanel {

    private final PullRequestService pullRequestService;

    @Inject
    public PullRequestTabPanel(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @Override
    public List<IssueAction> getActions(Issue issue, com.atlassian.jira.user.ApplicationUser remoteUser) {
        List<IssueAction> actions = new ArrayList<>();
        List<PullRequestModel> pullRequests = pullRequestService.getPullRequests(issue.getKey());
        if (pullRequests != null && !pullRequests.isEmpty()) {
            actions.add(new PullRequestIssueAction(descriptor, pullRequests));
        }
        return actions;
    }

    @Override
    public boolean showPanel(Issue issue, ApplicationUser remoteUser) {
        return pullRequestService.hasPullRequests(issue.getKey());
    }
}
