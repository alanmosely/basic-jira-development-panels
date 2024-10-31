package com.alanmosely.jira.plugin.impl;

import com.alanmosely.jira.plugin.api.PullRequestModel;

import java.util.List;

public interface PullRequestService {
    void createPullRequest(String issueKey, PullRequestModel model);
    List<PullRequestModel> getPullRequests(String issueKey);
    boolean hasPullRequests(String issueKey);
}
