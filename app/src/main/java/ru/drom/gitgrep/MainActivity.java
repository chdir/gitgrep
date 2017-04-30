package ru.drom.gitgrep;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.drom.gitgrep.server.AuthFailedException;
import ru.drom.gitgrep.server.GithubServerException;
import ru.drom.gitgrep.server.RowFetcher;
import ru.drom.gitgrep.service.GithubAuth;
import ru.drom.gitgrep.service.GithubAuthenticator;
import ru.drom.gitgrep.util.RxAndroid;
import ru.drom.gitgrep.util.Utils;
import ru.drom.gitgrep.view.SaneDecor;
import ru.drom.gitgrep.view.FastScrollingView;
import ru.drom.gitgrep.view.SearchAdapter;
import ru.drom.gitgrep.view.SearchLayout;
import ru.drom.gitgrep.view.SearchLayoutManager;
import rx.subjects.PublishSubject;

import static android.net.ConnectivityManager.EXTRA_NO_CONNECTIVITY;
import static ru.drom.gitgrep.service.GithubAuthenticator.GITGREP_ACCOUNT_TYPE;
import static ru.drom.gitgrep.service.GithubAuthenticator.AUTH_TOKEN_ANY;

public final class MainActivity extends BaseActivity implements SearchLayout.SearchListener, RowFetcher.OnError {
    private static final String TAG = "MainActivity";

    private final SnackbarHandler snackbarHandler = new SnackbarHandler();

    private PublishSubject<ActivityEvent> lifecycle;
    private State state;
    private Context appContext;
    private ConnectivityManager cm;
    private ConnectivityObserver connObserver;
    private RecyclerView.AdapterDataObserver quickScrollObserver;

    @BindView(R.id.act_main_content)
    CoordinatorLayout contentLayout;

    @BindView(R.id.act_main_quick_scroll)
    FastScrollingView quickScroll;

    @BindView(R.id.act_main_list)
    RecyclerView searchesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        registerReceiver(
                connObserver = new ConnectivityObserver(),
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        setContentView(R.layout.act_main);

        ButterKnife.bind(this);

        searchesList.setHasFixedSize(true);
        searchesList.setItemAnimator(new DefaultItemAnimator());
        searchesList.addItemDecoration(new SaneDecor(this, LinearLayout.VERTICAL));
        searchesList.setLayoutManager(new SearchLayoutManager(this));
        searchesList.addOnScrollListener(quickScroll.getOnScrollListener());

        appContext = getApplication();

        lifecycle = PublishSubject.create();

        final State last = (State) getLastNonConfigurationInstance();
        if (last == null) {
            state = new State();
            state.listAdapter = new SearchAdapter();
        } else {
            state = last;
        }

        state.listAdapter.registerAdapterDataObserver(quickScrollObserver = quickScroll.getAdapterDataObserver());

        quickScroll.setRecyclerView(searchesList);

        final Resources res = getResources();
        if (state.bitmapCaches == null || !state.bitmapCaches.canReuse(res)) {
            state.bitmapCaches = new Bitmaps(res);
        }

        if (!state.ready) {
            ensureAuthenticated();
        } else {
            proceedWithStartup(state.account);
        }
    }

    private void ensureAuthenticated() {
        RxAndroid.requireAccountWithAuthToken(this, GITGREP_ACCOUNT_TYPE, AUTH_TOKEN_ANY)
                .subscribeOn(RxAndroid.bgLooperThread())
                .observeOn(RxAndroid.mainThread())
                .compose(RxLifecycle.bindUntilActivityEvent(lifecycle, ActivityEvent.DESTROY))
                .subscribe(this::proceedWithStartup, this::handleSignInError);
    }

    private void proceedWithStartup(Account unused) {
        assert searchesList != null;

        state.ready = true;
        state.account = unused;

        searchesList.setAdapter(state.listAdapter);

        invalidateOptionsMenu();
    }

    private Bundle savedInstanceState;

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // postpone View state restoration until the action bar menu is fully initialized
        this.savedInstanceState = savedInstanceState;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_main_search);

        final SearchLayout searchView = (SearchLayout) searchItem.getActionView();

        searchView.setSearchListener(this);

        if (savedInstanceState != null) {
            super.onRestoreInstanceState(savedInstanceState);
            savedInstanceState = null;
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.menu_main_search);

        searchItem.setEnabled(state.ready);

        final MenuItem logout = menu.findItem(R.id.menu_main_logout);
        final MenuItem login = menu.findItem(R.id.menu_main_login);

        final boolean anon = AppPrefs.isAnonymousByChoice(this);

        login.setVisible(anon);
        logout.setVisible(!anon);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_main_logout:
            case R.id.menu_main_login:
                logout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressWarnings("deprecation")
    private void logout() {
        AppPrefs.setAnonymousByChoice(this, false);

        final GithubAuth auth = GithubAuth.getInstance(this);

        auth.invalidateToken();

        final AccountManager am = AccountManager.get(this);

        final Account[] account = am.getAccountsByType(GithubAuthenticator.GITGREP_ACCOUNT_TYPE);

        if (account.length == 0) {
            ensureAuthenticated();

            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            am.removeAccountExplicitly(account[0]);

            ensureAuthenticated();
        } else {
            am.removeAccount(account[0], future -> ensureAuthenticated(), null);
        }
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        switch (name) {
            case Bitmaps.SERVICE_BITMAPS:
                return state.bitmapCaches;
            default:
                return super.getSystemService(name);
        }
    }

    private void handleSignInError(Throwable error) {
        if (AppPrefs.isAnonymousByChoice(this)) {
            proceedWithStartup(null);
        } else {
            if (!(error instanceof OperationCanceledException)) {
                Utils.logError(appContext, error);
            }

            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        lifecycle.onNext(ActivityEvent.START);
    }

    @Override
    protected void onStop() {
        lifecycle.onNext(ActivityEvent.STOP);

        super.onStop();
    }

    @Override
    protected void onPause() {
        lifecycle.onNext(ActivityEvent.PAUSE);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        lifecycle.onNext(ActivityEvent.RESUME);
    }

    @Override
    protected void onDestroy() {
        lifecycle.onNext(ActivityEvent.DESTROY);

        if (connObserver != null) {
            unregisterReceiver(connObserver);
        }

        if (quickScrollObserver != null) {
            state.listAdapter.unregisterAdapterDataObserver(quickScrollObserver);
        }

        if (rowFetcher != null) {
            rowFetcher.setErrorHandler(null);
        }

        super.onDestroy();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }

    private RowFetcher rowFetcher;

    @Override
    public void search(CharSequence query) {
        if (isFinishing()) {
            return;
        }

        if (query.length() == 0) {
            rowFetcher = null;
        } else {
            rowFetcher = new RowFetcher(appContext, query.toString());
            rowFetcher.setErrorHandler(this);
        }

        state.listAdapter.setQuery(rowFetcher);
    }

    private Snackbar lastError;

    @Override
    public void onSuccess() {
        if (lastError != null) {
            lastError.dismiss();
            lastError = null;
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof AuthFailedException) {
            logout();

            Utils.toast(appContext, getString(R.string.search_err_access));

            return;
        }

        if (lastError != null) {
            return;
        }

        final NetworkInfo ni = cm.getActiveNetworkInfo();

        final Snackbar newError;

        if (ni != null && ni.isConnected()) {
            final String message;

            if (throwable instanceof GithubServerException) {
                final int status = ((GithubServerException) throwable).getStatus();

                switch (status) {
                    case 403:
                        message = getString(R.string.throttle_err);
                        break;
                    default:
                        message = getString(R.string.http_err, status);
                }
            } else {
                message = getString(R.string.search_err);
            }

            newError = Snackbar.make(contentLayout, message, Snackbar.LENGTH_LONG);
        } else {
            newError = Snackbar.make(contentLayout, R.string.netowrk_err, Snackbar.LENGTH_INDEFINITE);
        }

        newError.setCallback(snackbarHandler);
        newError.show();
    }

    private final class SnackbarHandler extends Snackbar.Callback {
        @Override
        public void onShown(Snackbar snackbar) {
            lastError = snackbar;
        }

        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            if (lastError == snackbar) {
                lastError = null;
            }
        }
    }

    private final class ConnectivityObserver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(EXTRA_NO_CONNECTIVITY) && intent.getBooleanExtra(EXTRA_NO_CONNECTIVITY, false)) {
                return;
            }

            if (state.listAdapter != null) {
                // retry botched image loading jobs
                state.listAdapter.notifyDataSetChanged();
            }

            if (lastError != null) {
                lastError.dismiss();
                lastError = null;
            }
        }
    }

    private static class State {
        SearchAdapter listAdapter;
        Bitmaps bitmapCaches;
        Account account;
        boolean ready;
    }
}
