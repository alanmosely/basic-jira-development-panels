package com.alanmosely.jira.plugin.ao;

import net.java.ao.Entity;
import net.java.ao.schema.Table;

@Table("PullRequest")
public interface PullRequestEntity extends Entity {
    String getIssueKey();
    void setIssueKey(String issueKey);

    String getName();
    void setName(String name);

    String getUrl();
    void setUrl(String url);

    String getStatus();
    void setStatus(String status);
   
    String getRepoName();
    void setRepoName(String repoName);
    
    String getRepoUrl();
    void setRepoUrl(String repoUrl);
 
    String getBranchName();
    void setBranchName(String branchName);
}