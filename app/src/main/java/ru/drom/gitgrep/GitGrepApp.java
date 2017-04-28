package ru.drom.gitgrep;

import android.app.Application;
import android.content.SharedPreferences;
import android.net.http.HttpResponseCache;
import android.preference.PreferenceManager;
import android.util.Log;

import org.codegist.crest.CRest;
import org.codegist.crest.CRestBuilder;
import org.codegist.crest.CRestConfig;
import org.codegist.crest.security.Authorization;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import ru.drom.gitgrep.server.GithubApi;
import ru.drom.gitgrep.server.GithubBaseApi;
import ru.drom.gitgrep.server.LoganDecoder;
import ru.drom.gitgrep.service.GithubAuth;

public final class GitGrepApp extends Application {
    public static final String APP_SCHEME = "ru.drom.gitgrep";

    private static final String TAG = "GitGrepApp";

    private GithubApi githubApi;
    private GithubBaseApi authApi;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
            }
        });

        long cacheSize;
        File cacheDir = getExternalCacheDir();
        if (cacheDir != null) {
            cacheSize = 1024 * 1024 * 200;
        } else {
            cacheDir = getCacheDir();
            cacheSize = 1024 * 1024 * 20;
        }
        try {
            HttpResponseCache.install(cacheDir, cacheSize);
        } catch (IOException e) {
            Log.i(TAG, "Failed to initialize HTTP cache");
        }

        final CRest crest = new CRestBuilder()
                .endpoint("https://api.github.com")
                .property(Authorization.class.getName(), GithubAuth.getInstance(this))
                .deserializeJsonWith(LoganDecoder.class)
                .bindJsonDeserializerWith("application/vnd.github.mercy-preview+json")
                .build();

        githubApi = crest.build(GithubApi.class);

        final CRest authCrest = new CRestBuilder()
                .endpoint("https://github.com")
                .deserializeJsonWith(LoganDecoder.class)
                .bindJsonDeserializerWith("application/vnd.github.mercy-preview+json")
                .build();

        authApi = authCrest.build(GithubBaseApi.class);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public Object getSystemService(String name) {
        switch (name) {
            case GithubApi.SERVICE_API:
                return githubApi;
            case AppPrefs.SERVICE_APP_PREFS:
                return preferences;
            case GithubBaseApi.AUTH_API:
                return authApi;
            default:
                return super.getSystemService(name);
        }
    }
}
