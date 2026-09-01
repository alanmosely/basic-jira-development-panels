package com.alanmosely.jira.plugin.admin;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.websudo.WebSudoRequired;

@WebSudoRequired
public class PullRequestAdminAction extends JiraWebActionSupport {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestAdminAction.class);

    private final PluginSettingsFactory pluginSettingsFactory;
    private boolean notificationsEnabled;
    private String apiUser;

    @Inject
    public PullRequestAdminAction(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    @SupportedMethods({ RequestMethod.GET })
    public String doDefault() {
        logger.debug("Entering doDefault()");
        loadSettings();
        return INPUT;
    }

    /**
     * GET view of the settings page. Kept alongside doDefault so the pre-2.0
     * canonical URL (/secure/admin/PullRequestAdmin.jspa — bookmarks, runbooks,
     * websudo redirects) still renders the form instead of a 405.
     */
    @Override
    @SupportedMethods({ RequestMethod.GET })
    public String doExecute() {
        logger.debug("Entering doExecute()");
        loadSettings();
        return INPUT;
    }

    @SupportedMethods({ RequestMethod.POST })
    @RequiresXsrfCheck
    public String doSave() {
        logger.debug("Entering doSave()");
        pluginSettingsFactory.createGlobalSettings().put(SettingsKeys.NOTIFICATIONS_ENABLED_KEY,
                Boolean.toString(notificationsEnabled));

        pluginSettingsFactory.createGlobalSettings().put(SettingsKeys.API_USER_KEY,
                StringUtils.isBlank(apiUser) ? null : apiUser.trim());
        logger.debug("Saved notificationsEnabled={}, apiUser={}", notificationsEnabled, apiUser);

        return SUCCESS;
    }

    private void loadSettings() {
        String notificationsValue = (String) pluginSettingsFactory.createGlobalSettings()
                .get(SettingsKeys.NOTIFICATIONS_ENABLED_KEY);
        notificationsEnabled = notificationsValue == null || Boolean.parseBoolean(notificationsValue);

        String apiUserValue = (String) pluginSettingsFactory.createGlobalSettings().get(SettingsKeys.API_USER_KEY);
        apiUser = StringUtils.defaultIfBlank(apiUserValue, "");
    }

    public boolean areNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getApiUser() {
        return apiUser;
    }

    public void setApiUser(String apiUser) {
        this.apiUser = apiUser;
    }
}
