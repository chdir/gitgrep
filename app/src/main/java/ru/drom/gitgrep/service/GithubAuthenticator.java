package ru.drom.gitgrep.service;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import ru.drom.gitgrep.AppPrefs;
import ru.drom.gitgrep.AuthActivity;
import ru.drom.gitgrep.R;
import ru.drom.gitgrep.util.Utils;

public final class GithubAuthenticator extends AbstractAccountAuthenticator {
    public static final String ACCOUNT_TYPE = "ru.drom.gitgrep";
    public static final String AUTH_TOKEN_TYPE = "gitgrep_auth_default";

    public static final String EXTRA_SILENT = "silent";

    private final Context context;
    private final AccountManager am;

    public GithubAuthenticator(Context context) {
        super(context);

        this.context = context;
        this.am = AccountManager.get(context);
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle transport) throws NetworkErrorException {
        if (transport == null) {
            transport = new Bundle();
        }

        transport.putParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        final Account[] existingAccounts = am.getAccountsByType(ACCOUNT_TYPE);

        transport.putParcelableArray(AccountManager.KEY_ACCOUNTS, existingAccounts);

        if (existingAccounts.length == 0)
        {
            return keyIntent(context, transport);
        }
        else if (!transport.containsKey(EXTRA_SILENT))
        {
            Utils.runOnMain(context, () -> Utils.toast(context, "You can have only one account of this type"));

            return unsupportedOperation();
        }
        else
        {
            // ignore
            return null;
        }
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        final Bundle result = options == null ? new Bundle(3) : new Bundle(options);

        if (AppPrefs.isAnonymousByChoice(context)) {
            result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_AUTHENTICATION);
            return result;
        }

        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        result.putParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        final GithubAuth auth = GithubAuth.getInstance(context);

        String token = auth.getCurrentToken();
        if (token == null)
        {
            try {
                auth.refresh();

                token = auth.getCurrentToken();
            } catch (Exception ignore) {
                // ok
            }

            if (token == null)
            {
                return keyIntent(context, result);
            }
        }

        result.putString(AccountManager.KEY_AUTHTOKEN, token);

        return result;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return context.getString(R.string.app_name);
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        final Bundle result = options == null ? new Bundle(2) : new Bundle(options);

        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);

        return result;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
        final Bundle result = new Bundle(1);

        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);

        return result;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

    private Bundle keyIntent(Context context, Bundle transport) {
        final Intent key = new Intent(context, AuthActivity.class);

        key.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        key.putExtras(transport);

        transport.putParcelable(AccountManager.KEY_INTENT, key);

        return transport;
    }

    private static Bundle unsupportedOperation()
    {
        Bundle result = new Bundle(2);

        result.putString(AccountManager.KEY_ERROR_MESSAGE, "Unsupported operation");
        result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);

        return result;
    }
}
