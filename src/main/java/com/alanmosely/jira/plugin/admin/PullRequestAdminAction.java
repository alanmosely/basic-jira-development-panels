package com.alanmosely.jira.plugin.admin;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.security.request.RequestMethod;
import com.atlassian.jira.security.request.SupportedMethods;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.websudo.WebSudoRequired;

@WebSudoRequired
public class PullRequestAdminAction extends JiraWebActionSupport {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestAdminAction.class);

    private static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";
    private static final String NOTIFICATIONS_ENABLED_KEY = PLUGIN_STORAGE_KEY + ".notificationsEnabled";
    private static final String API_USER_KEY = PLUGIN_STORAGE_KEY + ".apiUser";

    private final PluginSettingsFactory pluginSettingsFactory;
    private boolean notificationsEnabled;
    private String apiUser;

    @Inject
    public PullRequestAdminAction(PluginSettingsFactory pluginSettingsFactory) {
        logger.debug("PullRequestAdminAction constructor called");
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String doDefault() {
        logger.debug("Entering doDefault()");
        String notificationsValue = (String) pluginSettingsFactory.createGlobalSettings()
                .get(NOTIFICATIONS_ENABLED_KEY);
        notificationsEnabled = notificationsValue == null || Boolean.parseBoolean(notificationsValue);
        logger.debug("notificationsEnabled set to: {}", notificationsEnabled);

        String apiUserValue = (String) pluginSettingsFactory.createGlobalSettings().get(API_USER_KEY);
        apiUser = StringUtils.defaultIfBlank(apiUserValue, "");
        logger.debug("apiUser set to: {}", apiUser);

        return INPUT;
    }

    @Override
    @SupportedMethods({ RequestMethod.GET, RequestMethod.POST })
    public String doExecute() throws Exception {
        logger.debug("Entering doExecute()");
        if (RequestMethod.POST.toString().equals(getHttpRequest().getMethod())) {
            logger.debug("Handling POST request");
            pluginSettingsFactory.createGlobalSettings().put(NOTIFICATIONS_ENABLED_KEY,
                    Boolean.toString(notificationsEnabled));
            logger.debug("notificationsEnabled set to: {}", notificationsEnabled);

            pluginSettingsFactory.createGlobalSettings().put(API_USER_KEY,
                    StringUtils.isBlank(apiUser) ? null : apiUser.trim());
            logger.debug("apiUser set to: {}", apiUser);

            return SUCCESS;
        } else {
            logger.debug("Handling GET request");
            String notificationsValue = (String) pluginSettingsFactory.createGlobalSettings()
                    .get(NOTIFICATIONS_ENABLED_KEY);
            notificationsEnabled = notificationsValue == null || Boolean.parseBoolean(notificationsValue);
            logger.debug("notificationsEnabled set to: {}", notificationsEnabled);

            String apiUserValue = (String) pluginSettingsFactory.createGlobalSettings().get(API_USER_KEY);
            apiUser = StringUtils.defaultIfBlank(apiUserValue, "");
            logger.debug("apiUser set to: {}", apiUser);

            return INPUT;
        }
    }

    public boolean areNotificationsEnabled() {
        logger.debug("areNotificationsEnabled() called, returning: {}", notificationsEnabled);
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        logger.debug("setNotificationsEnabled() called with: {}", notificationsEnabled);
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getApiUser() {
        logger.debug("getApiUser() called, returning: {}", apiUser);
        return apiUser;
    }

    public void setApiUser(String apiUser) {
        logger.debug("setApiUser() called with: {}", apiUser);
        this.apiUser = apiUser;
    }
}
