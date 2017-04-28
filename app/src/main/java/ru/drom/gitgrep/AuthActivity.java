package ru.drom.gitgrep;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import butterknife.ButterKnife;
import butterknife.OnClick;
import ru.drom.gitgrep.server.AuthResults;
import ru.drom.gitgrep.server.GithubApi;
import ru.drom.gitgrep.server.GithubBaseApi;
import ru.drom.gitgrep.service.GithubAuth;
import ru.drom.gitgrep.service.GithubAuthenticator;
import ru.drom.gitgrep.util.RxAndroid;
import ru.drom.gitgrep.util.Utils;
import rx.Observable;

public final class AuthActivity extends Activity {
    private static final String TAG = "AuthActivity";

    private Bundle transport;
    private AccountAuthenticatorResponse response;
    private Context appContext;
    private AccountManager am;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.act_auth);

        appContext = getApplication();

        am = AccountManager.get(appContext);

        transport = getIntent().getExtras();
        if (transport != null) {
            response = transport.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
        }

        if (savedInstanceState != null && response == null) {
            response = savedInstanceState.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
        }

        if (response != null) {
            response.onRequestContinued();
        }

        ButterKnife.bind(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        final String action = intent.getAction();

        if (!Intent.ACTION_VIEW.equals(action)) return;

        final Uri uri = intent.getData();

        if (uri == null) return;

        if (!GitGrepApp.APP_SCHEME.equals(uri.getScheme())) return;

        final String code = uri.getQueryParameter("code");

        if (TextUtils.isEmpty(code)) return;

        Observable.fromCallable(() -> login(code))
                .subscribeOn(RxAndroid.bgLooperThread())
                .observeOn(RxAndroid.mainThread())
                .subscribe(this::returnResult);
    }

    private AuthResults login(String code) {
        final GithubBaseApi base = (GithubBaseApi) appContext.getSystemService(GithubBaseApi.AUTH_API);

        final AuthResults results = base.login(GithubApi.CLIENT_ID, GithubBaseApi.HAZARD, code);

        final Account acc = new Account("Default account", GithubAuthenticator.ACCOUNT_TYPE);

        if (!am.addAccountExplicitly(acc, "", Bundle.EMPTY)) {
            Log.e(TAG, "Failed to add account, ignoring");
        }

        final GithubAuth auth = GithubAuth.getInstance(appContext);

        auth.setCurrentToken(results.accessToken, results.tokenType);

        am.setAuthToken(acc, GithubAuthenticator.ACCOUNT_TYPE, results.accessToken);

        return results;
    }

    private void returnResult(AuthResults authInfo) {

        final Account[] accs = am.getAccountsByType(GithubAuthenticator.ACCOUNT_TYPE);

        if (accs.length != 0)
        {
            transport.putParcelableArray(AccountManager.KEY_ACCOUNTS, accs);
            transport.putString(AccountManager.KEY_AUTHTOKEN, authInfo.accessToken);
            transport.putString(AccountManager.KEY_ACCOUNT_TYPE, GithubAuthenticator.ACCOUNT_TYPE);
            transport.putString(AccountManager.KEY_ACCOUNT_NAME, "Default account");
        }


        finish();
    }

    @OnClick(R.id.auth_btn_pass)
    void enterPassClicked() {
        try {
            final String uri = "https://github.com/login/oauth/authorize?client_id=" + GithubApi.CLIENT_ID;

            final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

            startActivity(browserIntent);
        } catch (Throwable exception) {
            Utils.toast(this, "Failed to start web browser");
        }
    }

    @OnClick(R.id.auth_btn_token)
    void enterTokenClicked() {
        Utils.toast(this, "TODO");
    }

    @OnClick(R.id.auth_btn_anon_use)
    void anonUseClicked() {
        Utils.toast(this, "TODO");
    }

    public void finish() {
        if (response != null) {
            if (transport.containsKey(AccountManager.KEY_AUTHTOKEN)) {
                response.onResult(transport);
            } else {
                response.onError(AccountManager.ERROR_CODE_CANCELED, "cancel");
            }
        }
        super.finish();
    }
}
