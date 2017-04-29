package ru.drom.gitgrep;

import android.app.Application;
import android.content.SharedPreferences;
import android.net.http.HttpResponseCache;
import android.os.Binder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.codegist.crest.CRest;
import org.codegist.crest.CRestBuilder;
import org.codegist.crest.CRestConfig;
import org.codegist.crest.config.MethodConfig;
import org.codegist.crest.security.Authorization;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import ru.drom.gitgrep.server.AuthRequestInterceptor;
import ru.drom.gitgrep.server.ErrorCodeHandler;
import ru.drom.gitgrep.server.GithubApi;
import ru.drom.gitgrep.server.GithubBaseApi;
import ru.drom.gitgrep.server.LoganDecoder;
import ru.drom.gitgrep.service.GithubAuth;

public final class GitGrepApp extends Application {
    public static final String APP_SCHEME = "ru.drom.gitgrep";

    public static final String CREST_CONTEXT = "ru.drom.gitgrep.context";

    private static final String TAG = "GitGrepApp";

    private GithubApi githubApi;
    private GithubBaseApi authApi;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler wrapped = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Binder.flushPendingCommands();

            e.printStackTrace();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            wrapped.uncaughtException(t, e);
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
                .property(CREST_CONTEXT, this)
                .property(MethodConfig.METHOD_CONFIG_DEFAULT_ERROR_HANDLER, ErrorCodeHandler.class)
                .property(MethodConfig.METHOD_CONFIG_DEFAULT_REQUEST_INTERCEPTOR, AuthRequestInterceptor.class)
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
