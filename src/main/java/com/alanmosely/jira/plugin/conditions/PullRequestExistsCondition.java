package com.alanmosely.jira.plugin.conditions;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;

@Named
public class PullRequestExistsCondition implements Condition {

    private static final Logger log = LoggerFactory.getLogger(PullRequestExistsCondition.class);

    private final PullRequestService pullRequestService;

    @Inject
    public PullRequestExistsCondition(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
        log.debug("Entering shouldDisplay method with context: {}", context);

        Issue issue = (Issue) context.get("issue");
        if (issue == null) {
            log.warn("Issue is null in shouldDisplay method.");
            return false;
        }

        try {
            boolean hasPullRequests = pullRequestService.hasPullRequests(issue.getKey());
            log.debug("Issue {} has pull requests: {}", issue.getKey(), hasPullRequests);
            return hasPullRequests;
        } catch (Exception e) {
            log.error("Error checking pull requests for issue {}", issue.getKey(), e);
            return false;
        }
    }
}
