package com.alanmosely.jira.plugin.impl;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import net.java.ao.Query;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.List;

@Named
public class PullRequestServiceImpl implements PullRequestService {

    private final ActiveObjects activeObjects;

    @Inject
    public PullRequestServiceImpl(@ComponentImport ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void createPullRequest(String issueKey, PullRequestModel model) {
        PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
        entity.setIssueKey(issueKey);
        entity.setName(model.getName());
        entity.setUrl(model.getUrl());
        entity.setStatus(model.getStatus());
        entity.save();
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class, Query.select().where("ISSUE_KEY = ?", issueKey));
        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            PullRequestModel model = new PullRequestModel();
            model.setName(entity.getName());
            model.setUrl(entity.getUrl());
            model.setStatus(entity.getStatus());
            models.add(model);
        }
        return models;
    }

    @Override
    public boolean hasPullRequests(String issueKey) {
        int count = activeObjects.count(PullRequestEntity.class, Query.select().where("ISSUE_KEY = ?", issueKey));
        return count > 0;
    }
}
