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

import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;

import butterknife.ButterKnife;
import butterknife.OnClick;
import ru.drom.gitgrep.server.AuthResults;
import ru.drom.gitgrep.server.GithubApi;
import ru.drom.gitgrep.server.GithubBaseApi;
import ru.drom.gitgrep.service.GithubAuth;
import ru.drom.gitgrep.service.GithubAuthenticator;
import ru.drom.gitgrep.util.RxAndroid;
import ru.drom.gitgrep.util.Utils;
import ru.drom.gitgrep.view.PasswordPromptFragment;
import rx.Observable;
import rx.subjects.PublishSubject;

public final class AuthActivity extends Activity implements PasswordPromptFragment.CredentialsReceiver {
    private static final String TAG = "AuthActivity";

    private final PublishSubject<ActivityEvent> lifecycle = PublishSubject.create();

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

        if (!TextUtils.isEmpty(code)) {
            Observable.fromCallable(() -> login(code))
                    .subscribeOn(RxAndroid.bgLooperThread())
                    .observeOn(RxAndroid.mainThread())
                    .compose(RxLifecycle.bindUntilActivityEvent(lifecycle, ActivityEvent.DESTROY))
                    .subscribe(this::returnResult, err -> Utils.logError(appContext, err));

            return;
        }

        final String error = uri.getQueryParameter("error");

        if (!TextUtils.isEmpty(error)) {
            handleGithubAuthError(uri, error);
        }
    }

    private void handleGithubAuthError(Uri uri, String errorCode) {
        switch (errorCode) {
            case GithubBaseApi.ERR_SUSPENDED:
                Utils.toast(this, getString(R.string.auth_err_app_banned));
                break;
            case GithubBaseApi.ERR_DENIED:
                finish();
                break;
            default:
                final String desc = uri.getQueryParameter("error_description");
                if (desc != null) {
                    Log.w(TAG, "Login failed: " + desc);
                }

                Utils.toast(this, getString(R.string.auth_err_unknown));
        }
    }

    @Override
    public void onCredentials(String name, String password) {
        final Account acc = new Account(name, GithubAuthenticator.GITGREP_ACCOUNT_TYPE);

        if (!am.addAccountExplicitly(acc, password, Bundle.EMPTY)) {
            Log.e(TAG, "Failed to add account, ignoring");
        }

        final GithubAuth auth = GithubAuth.getInstance(appContext);
        auth.setCurrentToken(GithubAuthenticator.TOKEN_BASIC, password);

        am.setAuthToken(acc, GithubAuthenticator.GITGREP_ACCOUNT_TYPE, password);
        am.setUserData(acc, GithubAuthenticator.TOKEN_TYPE, GithubAuthenticator.TOKEN_BASIC);

        final Account[] accs = new Account[] { acc };

        transport.putParcelableArray(AccountManager.KEY_ACCOUNTS, accs);
        transport.putString(AccountManager.KEY_ACCOUNT_NAME, name);
        transport.putString(AccountManager.KEY_PASSWORD, password);
        transport.putString(AccountManager.KEY_AUTHTOKEN, password);
        transport.putString(AccountManager.KEY_ACCOUNT_TYPE, GithubAuthenticator.GITGREP_ACCOUNT_TYPE);

        finish();
    }

    private AuthResults login(String code) {
        final GithubBaseApi base = (GithubBaseApi) appContext.getSystemService(GithubBaseApi.AUTH_API);

        final AuthResults results = base.login(GithubApi.CLIENT_ID, GithubBaseApi.HAZARD, code);

        final Account acc = new Account("Primary Github account", GithubAuthenticator.GITGREP_ACCOUNT_TYPE);

        if (!am.addAccountExplicitly(acc, null, Bundle.EMPTY)) {
            Log.e(TAG, "Failed to add account, ignoring");
        }

        final GithubAuth auth = GithubAuth.getInstance(appContext);
        auth.setCurrentToken(results.tokenType, results.accessToken);

        am.setAuthToken(acc, GithubAuthenticator.GITGREP_ACCOUNT_TYPE, results.accessToken);
        am.setUserData(acc, GithubAuthenticator.TOKEN_TYPE, results.tokenType);

        return results;
    }

    private void returnResult(AuthResults authInfo) {

        final Account[] accs = am.getAccountsByType(GithubAuthenticator.GITGREP_ACCOUNT_TYPE);

        if (accs.length != 0)
        {
            transport.putParcelableArray(AccountManager.KEY_ACCOUNTS, accs);
            transport.putString(AccountManager.KEY_AUTHTOKEN, authInfo.accessToken);
            transport.putString(AccountManager.KEY_ACCOUNT_TYPE, GithubAuthenticator.GITGREP_ACCOUNT_TYPE);
            transport.putString(AccountManager.KEY_ACCOUNT_NAME, "Default account");
        }

        finish();
    }

    @OnClick(R.id.auth_btn_pass)
    void enterPassClicked() {
        try {
            final String uri = "https://github.com/login/oauth/authorize?client_id=" + GithubApi.CLIENT_ID;

            final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(browserIntent);
        } catch (Throwable exception) {
            Utils.toast(this, "Failed to start web browser");
        }
    }

    @OnClick(R.id.auth_btn_token)
    void enterTokenClicked() {
        new PasswordPromptFragment().show(getFragmentManager(), null);
    }

    @OnClick(R.id.auth_btn_anon_use)
    void anonUseClicked() {
        AppPrefs.setAnonymousByChoice(this, true);

        transport.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_AUTHENTICATION);

        finish();
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

    @Override
    protected void onDestroy() {
        lifecycle.onNext(ActivityEvent.DESTROY);

        super.onDestroy();
    }
}
