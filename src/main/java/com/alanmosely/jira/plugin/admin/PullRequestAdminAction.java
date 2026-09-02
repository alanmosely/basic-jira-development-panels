package com.alanmosely.jira.plugin.admin;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alanmosely.jira.plugin.util.SettingsKeys;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.GlobalPermissionKey;
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
        if (!hasAdminPermission()) {
            return PERMISSION_VIOLATION_RESULT;
        }
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
        if (!hasAdminPermission()) {
            return PERMISSION_VIOLATION_RESULT;
        }
        loadSettings();
        return INPUT;
    }

    @SupportedMethods({ RequestMethod.POST })
    @RequiresXsrfCheck
    public String doSave() {
        logger.debug("Entering doSave()");
        if (!hasAdminPermission()) {
            return PERMISSION_VIOLATION_RESULT;
        }
        pluginSettingsFactory.createGlobalSettings().put(SettingsKeys.NOTIFICATIONS_ENABLED_KEY,
                Boolean.toString(notificationsEnabled));

        pluginSettingsFactory.createGlobalSettings().put(SettingsKeys.API_USER_KEY,
                StringUtils.isBlank(apiUser) ? null : apiUser.trim());
        logger.debug("Saved notificationsEnabled={}, apiUser={}", notificationsEnabled, apiUser);

        // Re-read what was persisted so the success view always renders stored state
        // (a param-less POST would otherwise render null/default field values).
        loadSettings();
        return SUCCESS;
    }

    /**
     * Websudo is re-authentication, not authorization, and can be disabled
     * instance-wide (jira.websudo.is.disabled), so the action verifies global admin
     * permission itself in addition to roles-required="admin" in the descriptor.
     */
    private boolean hasAdminPermission() {
        return ComponentAccessor.getGlobalPermissionManager()
                .hasPermission(GlobalPermissionKey.ADMINISTER, getLoggedInUser());
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
