package com.alanmosely.jira.plugin.admin;

import javax.inject.Inject;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.websudo.WebSudoRequired;

@WebSudoRequired
public class PullRequestAdminAction extends JiraWebActionSupport {

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";
    private final PluginSettingsFactory pluginSettingsFactory;
    private boolean notificationsEnabled;

    @Inject
    public PullRequestAdminAction(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String doDefault() {
        String value = (String) pluginSettingsFactory.createGlobalSettings()
                .get(PLUGIN_STORAGE_KEY + ".notificationsEnabled");
        notificationsEnabled = value == null || Boolean.parseBoolean(value);
        return INPUT;
    }

    @Override
    public String doExecute() {
        pluginSettingsFactory.createGlobalSettings()
                .put(PLUGIN_STORAGE_KEY + ".notificationsEnabled",
                        String.valueOf(notificationsEnabled));
        return SUCCESS;
    }

    public boolean areNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }
}
