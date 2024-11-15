package com.alanmosely.jira.plugin.impl;

import java.util.List;

import com.alanmosely.jira.plugin.api.PullRequestModel;

public interface PullRequestService {
    void createPullRequest(String issueKey, PullRequestModel model);
    List<PullRequestModel> getPullRequests(String issueKey);
    boolean hasPullRequests(String issueKey);
}
