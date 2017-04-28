package ru.drom.gitgrep.service;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.ContextWrapper;

import org.codegist.crest.config.MethodType;
import org.codegist.crest.param.EncodedPair;
import org.codegist.crest.security.Authorization;
import org.codegist.crest.security.AuthorizationToken;

import java.util.concurrent.atomic.AtomicReference;

import ru.drom.gitgrep.AppPrefs;
import ru.drom.gitgrep.server.GithubApi;

public final class GithubAuth extends ContextWrapper implements Authorization {
    private final AtomicReference<AuthorizationToken> currentToken = new AtomicReference<>();

    private final AccountManager am;
    private final GithubApi api;

    private static volatile GithubAuth instance;

    private GithubAuth(Context base) {
        super(base);

        am = AccountManager.get(this);
        api = (GithubApi) getSystemService(GithubApi.SERVICE_API);
    }

    public static GithubAuth getInstance(Context context) {
        if (instance == null) {
            synchronized (GithubAuth.class) {
                if (instance == null) {
                    instance = new GithubAuth(context);
                }
            }
        }

        return instance;
    }

    @Override
    public AuthorizationToken authorize(MethodType methodType, String url, EncodedPair... parameters) throws Exception {
        return currentToken.get();
    }

    @Override
    public void refresh() throws Exception {
        if (AppPrefs.isStoringPassword(this)) {
            // TODO
        }
    }

    public String getCurrentToken() {
        final AuthorizationToken result = currentToken.get();

        return result == null ? null : result.getValue();
    }

    public void setCurrentToken(String accessToken, String tokenType) {
        this.currentToken.set(new AuthorizationToken(accessToken, tokenType));
    }
}
