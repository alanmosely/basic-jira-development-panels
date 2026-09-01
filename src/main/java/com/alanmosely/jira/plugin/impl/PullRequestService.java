package com.alanmosely.jira.plugin.impl;

import java.util.List;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.jira.issue.Issue;

public interface PullRequestService {
    void createPullRequest(String issueKey, PullRequestModel model);

    List<PullRequestModel> getPullRequests(String issueKey);

    boolean hasPullRequests(String issueKey);

    /**
     * Overloads for callers that already hold the Issue (panels, conditions):
     * they skip the key-to-issue lookup that the String versions must perform.
     */
    List<PullRequestModel> getPullRequests(Issue issue);

    boolean hasPullRequests(Issue issue);
}
