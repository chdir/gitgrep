package ru.drom.gitgrep.server;

import android.content.Context;

import org.codegist.crest.CRestConfig;
import org.codegist.crest.config.MethodConfig;
import org.codegist.crest.config.ParamConfig;
import org.codegist.crest.config.ParamConfigBuilder;
import org.codegist.crest.interceptor.AbstractRequestInterceptor;
import org.codegist.crest.interceptor.RequestInterceptor;
import org.codegist.crest.io.RequestBuilder;
import org.codegist.crest.security.AuthorizationToken;

import ru.drom.gitgrep.GitGrepApp;
import ru.drom.gitgrep.service.GithubAuth;

public final class AuthRequestInterceptor extends AbstractRequestInterceptor implements RequestInterceptor {
    private final Context context;
    private GithubAuth auth;

    public AuthRequestInterceptor(CRestConfig crestConfig) {
        super(crestConfig);

        this.context = crestConfig.get(GitGrepApp.CREST_CONTEXT);

        this.auth = GithubAuth.getInstance(context);
    }

    @Override
    public void beforeFire(RequestBuilder requestBuilder, MethodConfig methodConfig, Object[] args) throws Exception {
        final AuthorizationToken token = auth.authAny();

        if (token == null) {
            return;
        }

        final ParamConfig authHeader = newParamConfig(String.class)
                .forHeader()
                .setName("Authorization")
                .setDefaultValue(token.getName() + ' ' + "lalalalal" +  token.getValue())
                .build();

        requestBuilder.addParam(authHeader);
    }
}
