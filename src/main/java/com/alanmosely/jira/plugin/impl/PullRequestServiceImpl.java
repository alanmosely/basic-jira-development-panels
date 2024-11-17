package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import com.alanmosely.jira.plugin.ao.PullRequestEntity;
import com.alanmosely.jira.plugin.api.PullRequestModel;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import net.java.ao.Query;

@Named
public class PullRequestServiceImpl implements PullRequestService {

    private final ActiveObjects activeObjects;

    @Inject
    public PullRequestServiceImpl(@ComponentImport ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void createPullRequest(String issueKey, PullRequestModel model) {
        PullRequestEntity entity = findOrCreateEntity(issueKey, model.getUrl());
        updateEntityFromModel(entity, model);
        entity.save();
    }

    private PullRequestEntity findOrCreateEntity(String issueKey, String url) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ? AND URL = ?", issueKey, url));
        if (entities.length > 0) {
            return entities[0];
        } else {
            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueKey(issueKey);
            return entity;
        }
    }

    private void updateEntityFromModel(PullRequestEntity entity, PullRequestModel model) {
        entity.setName(model.getName());
        entity.setUrl(model.getUrl());
        entity.setStatus(model.getStatus());
        entity.setRepoName(model.getRepoName());
        entity.setRepoUrl(model.getRepoUrl());
        entity.setBranchName(model.getBranchName());
        entity.setUpdated(new Date());
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ?", issueKey));
        return mapEntitiesToModels(entities);
    }

    private List<PullRequestModel> mapEntitiesToModels(PullRequestEntity[] entities) {
        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            models.add(mapEntityToModel(entity));
        }
        return models;
    }

    private PullRequestModel mapEntityToModel(PullRequestEntity entity) {
        PullRequestModel model = new PullRequestModel();
        model.setName(entity.getName());
        model.setUrl(entity.getUrl());
        model.setStatus(entity.getStatus());
        model.setRepoName(entity.getRepoName());
        model.setRepoUrl(entity.getRepoUrl());
        model.setBranchName(entity.getBranchName());
        model.setUpdated(entity.getUpdated());
        return model;
    }

    @Override
    public boolean hasPullRequests(String issueKey) {
        int count = activeObjects.count(PullRequestEntity.class, Query.select()
                .where("ISSUE_KEY = ?", issueKey)
                .limit(1));
        return count > 0;
    }
}
