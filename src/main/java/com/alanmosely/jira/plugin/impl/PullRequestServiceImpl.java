package com.alanmosely.jira.plugin.impl;

import java.util.ArrayList;
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
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ? AND URL = ?", issueKey, model.getUrl()));
        if (entities.length > 0) {
            PullRequestEntity entity = entities[0];
            entity.setName(model.getName());
            entity.setUrl(model.getUrl());
            entity.setStatus(model.getStatus());
            entity.setRepoName(model.getRepoName());
            entity.setRepoUrl(model.getRepoUrl());
            entity.setBranchName(model.getBranchName());
            entity.save();
        } else {
            PullRequestEntity entity = activeObjects.create(PullRequestEntity.class);
            entity.setIssueKey(issueKey);
            entity.setName(model.getName());
            entity.setUrl(model.getUrl());
            entity.setStatus(model.getStatus());
            entity.setRepoName(model.getRepoName());
            entity.setRepoUrl(model.getRepoUrl());
            entity.setBranchName(model.getBranchName());
            entity.save();
        }
    }

    @Override
    public List<PullRequestModel> getPullRequests(String issueKey) {
        PullRequestEntity[] entities = activeObjects.find(PullRequestEntity.class,
                Query.select().where("ISSUE_KEY = ?", issueKey));
        List<PullRequestModel> models = new ArrayList<>();
        for (PullRequestEntity entity : entities) {
            PullRequestModel model = new PullRequestModel();
            model.setName(entity.getName());
            model.setUrl(entity.getUrl());
            model.setStatus(entity.getStatus());
            model.setRepoName(entity.getRepoName());
            model.setRepoUrl(entity.getRepoUrl());
            model.setBranchName(entity.getBranchName());
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
