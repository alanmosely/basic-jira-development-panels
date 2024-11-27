package com.alanmosely.jira.plugin.tabpanel;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueTabPanel;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.user.ApplicationUser;

@Named
public class PullRequestTabPanel extends AbstractIssueTabPanel {

    private static final Logger log = LoggerFactory.getLogger(PullRequestTabPanel.class);

    private final PullRequestService pullRequestService;

    @Inject
    public PullRequestTabPanel(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @Override
    public List<IssueAction> getActions(Issue issue, ApplicationUser remoteUser) {
        log.debug("Entering getActions with issue: {}, remoteUser: {}", issue, remoteUser);

        List<IssueAction> actions = new ArrayList<>();
        try {
            List<PullRequestModel> pullRequests = pullRequestService.getPullRequests(issue.getKey());
            log.debug("Retrieved {} pull requests for issue {}", pullRequests != null ? pullRequests.size() : 0,
                    issue.getKey());

            if (pullRequests != null && !pullRequests.isEmpty()) {
                actions.add(new PullRequestIssueAction(descriptor, pullRequests));
                log.debug("Added PullRequestIssueAction to actions list for issue {}", issue.getKey());
            } else {
                log.debug("No pull requests found for issue {}", issue.getKey());
            }
        } catch (Exception e) {
            log.error("Error retrieving pull requests for issue {}", issue.getKey(), e);
        }

        log.debug("Exiting getActions with actions size: {}", actions.size());
        return actions;
    }

    @Override
    public boolean showPanel(Issue issue, ApplicationUser remoteUser) {
        log.debug("Entering showPanel with issue: {}, remoteUser: {}", issue, remoteUser);

        try {
            boolean hasPullRequests = pullRequestService.hasPullRequests(issue.getKey());
            log.debug("Issue {} has pull requests: {}", issue.getKey(), hasPullRequests);
            return hasPullRequests;
        } catch (Exception e) {
            log.error("Error checking pull requests for issue {}", issue.getKey(), e);
            return false;
        } finally {
            log.debug("Exiting showPanel for issue {}", issue.getKey());
        }
    }
}
