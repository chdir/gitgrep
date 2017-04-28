package ru.drom.gitgrep.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;

import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * A number of small wrappers for making some daunting tasks reactive-ish.
 */
public final class RxAndroid {
    private RxAndroid() {}

    /**
     * Create an account if no accounts of specified type exist yet.
     *<p>
     * This convenience methods won't create an account, unless no accounts of required type
     * exist. If you want to force account creation (for example, for custom account selection dialog),
     * use {@link OnSubscribeAddAccount} directly.
     *
     * @param b Builder with {@link Account} parameters.
     * @return Observable emitting existing Accounts (if any) or newly created Account.
     */
    public static Observable<Account> requireAccount(OnSubscribeAddAccount.Builder b) {
        final AccountManager am = AccountManager.get(b.activity.getApplication());
        final Account[] account = am.getAccountsByType(b.accountType);
        return account.length == 0 ? Observable.create(b.build()) : Observable.from(account);
    }

    /**
     * Create an account if no accounts of specified type exist yet.
     *<p>
     * This convenience methods won't create an account, unless no accounts of required type
     * exist. If you want to force account creation (for example, for custom account selection dialog),
     * use {@link OnSubscribeAddAccount} directly.
     *
     * @param activity used by {@link AccountManager} to launch Account creation Activity, if new Account is needed.
     * @param accountType type of Account.
     * @return Observable emitting existing Accounts (if any) or newly created Account.
     */
    public static Observable<Account> requireAccount(@NonNull Activity activity, @NonNull String accountType) {
        return requireAccount(new OnSubscribeAddAccount.Builder(activity, accountType));
    }

    /**
     * Create an account if no accounts of specified type exist yet.
     *<p>
     * In addition to creation of account this method ensures, that an auth token of fitting type
     * exists (e.g. the user can actually perform operations, that require account).
     *
     * @param activity used by {@link AccountManager} to launch Account creation Activity, if new Account is needed.
     * @param accountType type of Account.
     * @param authTokenType type of auth token.
     * @return Observable emitting existing Accounts (if any) or newly created Account.
     */
    public static Observable<Account> requireAccountWithAuthToken(
            @NonNull Activity activity, @NonNull String accountType, @NonNull String authTokenType) {
        return requireAccount(new OnSubscribeAddAccount.Builder(activity, accountType))
                .lift(new EnsureAuthTokenOperator(activity, authTokenType));
    }

    private static final Scheduler MAIN_THREAD_SCHEDULER = HandlerScheduler.INSTANCE;

    private static final HandlerThread bgThread = new HandlerThread("Recity background queue");

    private static final Scheduler BG_THREAD_SCHEDULER;

    static {
        bgThread.start();

        BG_THREAD_SCHEDULER = new HandlerScheduler(new Handler(bgThread.getLooper()));
    }

    /** A {@link Scheduler} which executes actions on the Android UI thread. */
    public static Scheduler mainThread() {
        return MAIN_THREAD_SCHEDULER;
    }

    public static Scheduler bgLooperThread() {
        return BG_THREAD_SCHEDULER;
    }

    /** A {@link Scheduler} which executes actions on the thread, associated with given Handler */
    public static Scheduler handlerThread(Handler handler) {
        return new HandlerScheduler(handler);
    }

    /**
     * Create a {@link Subscription} that always runs the specified {@code unsubscribe} on the
     * UI thread.
     */
    public static Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
        return Subscriptions.create(new Action0() {
            @Override
            public void call() {
                if (Looper.getMainLooper() == Looper.myLooper()) {
                    unsubscribe.call();
                } else {
                    final Scheduler.Worker inner = RxAndroid.mainThread().createWorker();
                    inner.schedule(new Action0() {
                        @Override
                        public void call() {
                            unsubscribe.call();
                            inner.unsubscribe();
                        }
                    });
                }
            }
        });
    }

    /**
     * Create a {@link Subscription} that always runs <code>unsubscribe</code> in the thread,
     * associated with given {@link Handler}.
     */
    public static Subscription unsubscribeInHandlerThread(final Action0 unsubscribe, final Handler handler) {
        return Subscriptions.create(new Action0() {
            @Override
            public void call() {
                if (handler.getLooper() == Looper.myLooper()) {
                    unsubscribe.call();
                } else {
                    final Scheduler.Worker inner = RxAndroid.handlerThread(handler).createWorker();
                    inner.schedule(new Action0() {
                        @Override
                        public void call() {
                            unsubscribe.call();
                            inner.unsubscribe();
                        }
                    });
                }
            }
        });
    }
}