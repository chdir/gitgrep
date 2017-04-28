package ru.drom.gitgrep.service;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.ContextWrapper;

import org.codegist.crest.config.MethodConfig;
import org.codegist.crest.config.MethodType;
import org.codegist.crest.config.ParamConfigBuilder;
import org.codegist.crest.interceptor.AbstractRequestInterceptor;
import org.codegist.crest.interceptor.RequestInterceptor;
import org.codegist.crest.io.RequestBuilder;
import org.codegist.crest.param.EncodedPair;
import org.codegist.crest.security.Authorization;
import org.codegist.crest.security.AuthorizationToken;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import ru.drom.gitgrep.AppPrefs;
import ru.drom.gitgrep.server.GithubApi;

import static ru.drom.gitgrep.service.GithubAuthenticator.TOKEN_BASIC;

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

    public AuthorizationToken authAny() throws AuthenticatorException, OperationCanceledException, IOException {
        if (AppPrefs.isAnonymousByChoice(this)) {
            return null;
        }

        final AuthorizationToken token = currentToken.get();

        if (token != null) {
            return token;
        }

        final Account[] accounts = am.getAccounts();

        if (accounts.length == 0) {
            return null;
        }

        final Account account = accounts[0];

        final String t = am.blockingGetAuthToken(account, GithubAuthenticator.AUTH_TOKEN_ANY, true);

        if (t == null) {
            return null;
        }

        final String tokenType = am.getUserData(account, GithubAuthenticator.TOKEN_TYPE);

        if (TOKEN_BASIC.equals(tokenType)) {
            return new AuthorizationToken(TOKEN_BASIC, account.name + ':' + t);
        } else {
            return new AuthorizationToken(tokenType, t);
        }
    }

    @Override
    public AuthorizationToken authorize(MethodType methodType, String url, EncodedPair... parameters) throws Exception {
        return authAny();
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

    public void setCurrentToken(String type, String token) {
        this.currentToken.set(new AuthorizationToken(type, token));
    }
}
