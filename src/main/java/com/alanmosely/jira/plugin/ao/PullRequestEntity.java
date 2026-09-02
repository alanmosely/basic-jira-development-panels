package com.alanmosely.jira.plugin.ao;

import java.util.Date;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

// Every String column is explicitly pinned to AO's 255-char default so the schema
// cannot drift from the REST layer's length validation (PullRequestResource.MAX_FIELD_LENGTH).
// URL must stay VARCHAR (not UNLIMITED/CLOB) because it is indexed and used as the upsert key.
@Preload
@Table("PullRequest")
public interface PullRequestEntity extends Entity {
    @Indexed
    @StringLength(255)
    String getIssueKey();

    void setIssueKey(String issueKey);

    @Indexed
    Long getIssueId();

    void setIssueId(Long issueId);

    @StringLength(255)
    String getName();

    void setName(String name);

    @Indexed
    @StringLength(255)
    String getUrl();

    void setUrl(String url);

    @StringLength(255)
    String getStatus();

    void setStatus(String status);

    @StringLength(255)
    String getRepoName();

    void setRepoName(String repoName);

    @StringLength(255)
    String getRepoUrl();

    void setRepoUrl(String repoUrl);

    @StringLength(255)
    String getBranchName();

    void setBranchName(String branchName);

    Date getUpdated();

    void setUpdated(Date updated);
}
