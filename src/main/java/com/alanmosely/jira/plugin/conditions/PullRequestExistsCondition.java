package com.alanmosely.jira.plugin.conditions;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.log4j.Logger;

import com.alanmosely.jira.plugin.impl.PullRequestService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;

@Named
public class PullRequestExistsCondition implements Condition {

    private static final Logger log = Logger.getLogger(PullRequestExistsCondition.class);
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
        Issue issue = (Issue) context.get("issue");
        if (issue == null) {
            log.debug("Issue is null in condition.");
            return false;
        }
        boolean hasPullRequests = pullRequestService.hasPullRequests(issue.getKey());
        log.debug("Issue " + issue.getKey() + " has pull requests: " + hasPullRequests);
        return hasPullRequests;
    }
}
