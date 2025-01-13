package com.alanmosely.jira.plugin.webpanel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.contextproviders.AbstractJiraContextProvider;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.user.ApplicationUser;

@Named
public class PullRequestWebPanel extends AbstractJiraContextProvider {

    private static final Logger log = LoggerFactory.getLogger(PullRequestWebPanel.class);

    private final PullRequestService pullRequestService;

    @Inject
    public PullRequestWebPanel(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @Override
    public Map<String, Object> getContextMap(ApplicationUser applicationUser, JiraHelper jiraHelper) {
        Map<String, Object> context = new HashMap<>();

        Issue issue = (Issue) jiraHelper.getContextParams().get("issue");
        if (issue == null) {
            log.debug("No issue found in contextParams");
            return context;
        }

        log.debug("Entering getContextMap with issue: {}, applicationUser: {}", issue.getKey(), applicationUser);

        try {
            List<PullRequestModel> pullRequests = pullRequestService.getPullRequests(issue.getKey());
            log.debug("Retrieved {} pull requests for issue {}",
                    (pullRequests != null ? pullRequests.size() : 0),
                    issue.getKey());

            if (pullRequests != null && !pullRequests.isEmpty()) {
                context.put("pullRequests", pullRequests);
                log.debug("Added pullRequests to context for issue {}", issue.getKey());
            } else {
                log.debug("No pull requests found for issue {}", issue.getKey());
            }
        } catch (Exception e) {
            log.error("Error retrieving pull requests for issue {}", issue.getKey(), e);
        }

        log.debug("Exiting getContextMap with context keys: {}", context.keySet());
        return context;
    }

}