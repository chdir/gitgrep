package ru.drom.gitgrep.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import rx.Observable;
import rx.Subscriber;

public final class EnsureAuthTokenOperator implements Observable.Operator<Account, Account> {
    private Activity activity;

    private final String tokenType;

    public EnsureAuthTokenOperator(Activity activity, String tokenType) {
        this.activity = activity;
        this.tokenType = tokenType;
    }

    @Override
    public Subscriber<? super Account> call(Subscriber<? super Account> subscriber) {
        return new Subscriber<Account>() {
            @Override
            public void onCompleted() {
                // ok, let's await callback to be executed
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(Account account) {
                final Looper currentLooper = Looper.myLooper();
                if (currentLooper == null) {
                    final Throwable err = new IllegalStateException("This operator must be used on looper thread!");

                    subscriber.onError(err);

                    return;
                }

                final AccountManager am = AccountManager.get(activity.getApplicationContext());
                final Handler handler = currentLooper == Looper.getMainLooper() ? null : new Handler(currentLooper);

                AccountManagerFuture<?> f = am.getAuthToken(account, tokenType, null, activity, new AccountManagerCallback<Bundle>() {
                    @Override
                    public void run(AccountManagerFuture<Bundle> future) {
                        if (subscriber.isUnsubscribed())
                            return;

                        Exception err = null;
                        try {
                            final Bundle tokenBundle = future.getResult();

                            if (tokenBundle.containsKey(AccountManager.KEY_AUTHTOKEN)) {
                                subscriber.onNext(account);
                            } else {
                                err = new IllegalStateException("Unable to obtain auth token!");
                            }
                        } catch (Exception e) {
                            err = e;
                        } finally {
                            if (err != null) {
                                subscriber.onError(err);
                            } else {
                                subscriber.onCompleted();
                            }
                        }
                    }
                }, handler);

                activity = null;

                subscriber.add(handler == null
                        ? RxAndroid.unsubscribeInUiThread(() -> f.cancel(true))
                        : RxAndroid.unsubscribeInHandlerThread(() -> f.cancel(true), handler));
            }
        };
    }
}
