package com.matejdro.wearvibrationcenter.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import java.util.Map;
import java.util.Set;

public class PerAppSharedPreferences implements SharedPreferences {
    private final SharedPreferences appSharedPreferences;
    private final SharedPreferences defaultSharedPreferences;

    private PerAppSharedPreferences(SharedPreferences appSharedPreferences, SharedPreferences defaultSharedPreferences) {
        this.appSharedPreferences = appSharedPreferences;
        this.defaultSharedPreferences = defaultSharedPreferences;
    }

    @Override
    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getString(String key, String defValue) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getString(key, defValue);
        } else {
            return defaultSharedPreferences.getString(key, defValue);
        }
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getStringSet(key, defValues);
        } else {
            return defaultSharedPreferences.getStringSet(key, defValues);
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getInt(key, defValue);
        } else {
            return defaultSharedPreferences.getInt(key, defValue);
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getLong(key, defValue);
        } else {
            return defaultSharedPreferences.getLong(key, defValue);
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getFloat(key, defValue);
        } else {
            return defaultSharedPreferences.getFloat(key, defValue);
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (appSharedPreferences.contains(key)) {
            return appSharedPreferences.getBoolean(key, defValue);
        } else {
            return defaultSharedPreferences.getBoolean(key, defValue);
        }
    }

    @Override
    public boolean contains(String key) {
        return appSharedPreferences.contains(key) || defaultSharedPreferences.contains(key);
    }

    @Override
    public Editor edit() {
        return new PerAppEditor(appSharedPreferences.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException();
    }

    private class PerAppEditor implements Editor
    {
        private final Editor targetEditor;

        public PerAppEditor(Editor targetEditor) {
            this.targetEditor = targetEditor;
        }

        @Override
        public Editor putString(String key, String value) {
            if (value.equals(defaultSharedPreferences.getString(key, value))) {
                targetEditor.remove(key);
            } else {
                targetEditor.putString(key, value);
            }

            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            if (values.equals(defaultSharedPreferences.getStringSet(key, values))) {
                targetEditor.remove(key);
            } else {
                targetEditor.putStringSet(key, values);
            }

            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            if (value == defaultSharedPreferences.getInt(key, value)) {
                targetEditor.remove(key);
            } else {
                targetEditor.putInt(key, value);
            }

            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            if (value == defaultSharedPreferences.getLong(key, value)) {
                targetEditor.remove(key);
            } else {
                targetEditor.putLong(key, value);
            }

            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            if (value == defaultSharedPreferences.getFloat(key, value)) {
                targetEditor.remove(key);
            } else {
                targetEditor.putFloat(key, value);
            }

            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            if (value == defaultSharedPreferences.getBoolean(key, value)) {
                targetEditor.remove(key);
            } else {
                targetEditor.putBoolean(key, value);
            }

            return this;
        }

        @Override
        public Editor remove(String key) {
            targetEditor.remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            targetEditor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return targetEditor.commit();
        }

        @Override
        public void apply() {
            targetEditor.apply();
        }
    }

    public static SharedPreferences getPerAppSharedPreferences(Context context, String appPackage)
    {
        if (PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS.equals(appPackage)) {
            return getDefaultAppSharedPreferences(context);
        } else {
            SharedPreferences defaultSharedPreferences = getDefaultAppSharedPreferences(context);
            String filteredPackage = getSharedPreferencesNameFromPackage(appPackage);
            SharedPreferences appSharedPreferences = context.getSharedPreferences(filteredPackage, Context.MODE_PRIVATE);

            return new PerAppSharedPreferences(appSharedPreferences, defaultSharedPreferences);
        }
    }

    public static SharedPreferences getDefaultAppSharedPreferences(Context context)
    {
        String filteredPackage = getSharedPreferencesNameFromPackage(PerAppSettings.VIRTUAL_APP_DEFAULT_SETTINGS);
        return context.getSharedPreferences(filteredPackage, Context.MODE_PRIVATE);
    }

    private static String getSharedPreferencesNameFromPackage(String pkg)
    {
        return "app_".concat(filterAppName(pkg));
    }

    private static String filterAppName(String name)
    {
        return name.replaceAll("[^0-9a-zA-Z ]", "_");
    }

}
