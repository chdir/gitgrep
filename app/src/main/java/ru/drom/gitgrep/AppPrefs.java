package ru.drom.gitgrep;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    public static final String SERVICE_APP_PREFS = "ru.dom.gitgrep.services.PREFS";;

    private static final String PREFS_ANONYMOUS = "anonymous";
    private static final String PREFS_STORE_PASS = "use_password";

    private AppPrefs(SharedPreferences prefs) {}

    @SuppressWarnings("WrongConstant")
    public static SharedPreferences getSharedPreferences(Context context) {
        final Context appContext = context.getApplicationContext();

        return (SharedPreferences) appContext.getSystemService(SERVICE_APP_PREFS);
    }

    public static boolean isAnonymousByChoice(Context context) {
        final SharedPreferences prefs = getSharedPreferences(context);

        return prefs.getBoolean(PREFS_ANONYMOUS, false);
    }

    public static void setAnonymousByChoice(Context context, boolean anon) {
        final SharedPreferences prefs = getSharedPreferences(context);

        prefs.edit().putBoolean(PREFS_ANONYMOUS, anon).apply();
    }

    public static boolean isStoringPassword(Context context) {
        final SharedPreferences prefs = getSharedPreferences(context);

        return prefs.getBoolean(PREFS_STORE_PASS, false);
    }
}
