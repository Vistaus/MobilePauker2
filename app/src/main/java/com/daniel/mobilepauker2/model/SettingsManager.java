package com.daniel.mobilepauker2.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.daniel.mobilepauker2.R;

/**
 * Created by Daniel on 17.03.2018.
 * Masterarbeit:
 * MobilePauker++ - Intuitiv, plattformübergreifend lernen
 * Daniel Fritsch
 * hs-augsburg
 */

public class SettingsManager {
    private static SettingsManager instance;

    public static SettingsManager instance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    @NonNull
    String getSettingsKey(Context context, Keys key) {
        switch (key) {
            case STM:
                return context.getString(R.string.stm_key);
            case USTM:
                return context.getString(R.string.ustm_key);
            case ABOUT:
                return context.getString(R.string.about);
            case AUTO_SAVE:
                return context.getString(R.string.auto_save);
            case AUTO_SYNC:
                return context.getString(R.string.auto_sync);
            case HIDE_TIMES:
                return context.getString(R.string.hide_times);
            case REPEAT_CARDS:
                return context.getString(R.string.repeat_cards_mode);
            case CASE_SENSITIV:
                return context.getString(R.string.case_sensitive);
            case FLIP_CARD_SIDES:
                return context.getString(R.string.flip_card_sides);
            case DB_PREFERENCE:
                return context.getString(R.string.associate_dropbox);
            case RETURN_FORGOTTEN_CARDS:
                return context.getString(R.string.return_forgotten_cards);
            case LEARN_NEW_CARDS_RANDOMLY:
                return context.getString(R.string.learn_new_cards_randomly);
            case ENABLE_EXPIRE_TOAST:
                return context.getString(R.string.expire_toast);
            case SHOW_NOTIFY:
                return context.getString(R.string.show_notification);
            default:
                return "";
        }
    }

    @NonNull
    private String getDefaultStringValue(Context context, Keys key) {
        switch (key) {
            case STM:
                return context.getString(R.string.stm_default);
            case USTM:
                return context.getString(R.string.ustm_default);
            case REPEAT_CARDS:
                return context.getString(R.string.repeat_cards_default);
            case FLIP_CARD_SIDES:
                return context.getString(R.string.flip_card_sides_default);
            case RETURN_FORGOTTEN_CARDS:
                return context.getString(R.string.return_forgotten_cards_default);
            default:
                return "";
        }
    }

    private boolean getDefaultBoolValue(Context context, Keys key) {
        switch (key) {
            case AUTO_SAVE:
                return context.getResources().getBoolean(R.bool.auto_save_default);
            case AUTO_SYNC:
                return context.getResources().getBoolean(R.bool.auto_sync_default);
            case HIDE_TIMES:
                return context.getResources().getBoolean(R.bool.auto_sync_default);
            case CASE_SENSITIV:
                return context.getResources().getBoolean(R.bool.auto_sync_default);
            case LEARN_NEW_CARDS_RANDOMLY:
                return context.getResources().getBoolean(R.bool.learn_new_cards_randomly_default);
            case ENABLE_EXPIRE_TOAST:
                return context.getResources().getBoolean(R.bool.expire_toast_default);
            case SHOW_NOTIFY:
                return context.getResources().getBoolean(R.bool.show_notification_default);
            default:
                return false;
        }
    }

    public boolean getBoolPreference(Context context, Keys key) {
        String prefKey = getSettingsKey(context, key);
        boolean defValue = getDefaultBoolValue(context, key);
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(prefKey, defValue);
    }

    public String getStringPreference(Context context, Keys key) {
        String prefKey = getSettingsKey(context, key);
        String defValue = getDefaultStringValue(context, key);
        return PreferenceManager.getDefaultSharedPreferences(context).getString(prefKey, defValue);
    }

    public enum Keys {
        FLIP_CARD_SIDES,
        CASE_SENSITIV,
        USTM,
        STM,
        HIDE_TIMES,
        REPEAT_CARDS,
        RETURN_FORGOTTEN_CARDS,
        AUTO_SAVE,
        ABOUT,
        LEARN_NEW_CARDS_RANDOMLY,
        ENABLE_EXPIRE_TOAST,
        AUTO_SYNC,
        DB_PREFERENCE,
        SHOW_NOTIFY
    }
}
