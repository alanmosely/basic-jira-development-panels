package com.alanmosely.jira.plugin.util;

/**
 * SAL plugin-settings keys shared by the admin screen, the REST resource and the
 * service. These are persisted names — changing them orphans existing settings.
 */
public final class SettingsKeys {

    public static final String PLUGIN_STORAGE_KEY = "com.alanmosely.jira.plugin.pullrequestadmin";
    public static final String NOTIFICATIONS_ENABLED_KEY = PLUGIN_STORAGE_KEY + ".notificationsEnabled";
    public static final String API_USER_KEY = PLUGIN_STORAGE_KEY + ".apiUser";

    private SettingsKeys() {
    }
}
