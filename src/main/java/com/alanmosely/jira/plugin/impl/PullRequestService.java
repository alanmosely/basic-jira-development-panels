package com.alanmosely.jira.plugin.impl;

import java.util.List;

import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.jira.issue.Issue;

public interface PullRequestService {
    void createPullRequest(String issueKey, PullRequestModel model);

    List<PullRequestModel> getPullRequests(String issueKey);

    boolean hasPullRequests(String issueKey);

    /**
     * Overloads for callers that already hold the Issue (the REST resource, panels,
     * conditions): they skip the key-to-issue lookup that the String versions must
     * perform. The issue must not be null.
     */
    void createPullRequest(Issue issue, PullRequestModel model);

    List<PullRequestModel> getPullRequests(Issue issue);

    boolean hasPullRequests(Issue issue);

    /**
     * Deletes every stored pull request for the issue whose URL matches
     * {@code url} (compared trimmed). Returns false when nothing matched.
     */
    boolean deletePullRequest(Issue issue, String url);
}
