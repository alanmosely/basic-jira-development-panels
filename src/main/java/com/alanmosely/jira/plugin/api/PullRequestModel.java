package com.alanmosely.jira.plugin.api;

import javax.xml.bind.annotation.*;

@XmlRootElement(name = "pullRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class PullRequestModel {

    private String name;
    private String url;
    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
