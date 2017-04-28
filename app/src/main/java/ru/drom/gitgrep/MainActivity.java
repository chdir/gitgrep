package ru.drom.gitgrep;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;

import com.trello.rxlifecycle.ActivityEvent;
import com.trello.rxlifecycle.RxLifecycle;

import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import ru.drom.gitgrep.server.GithubApi;
import ru.drom.gitgrep.server.RepositoryResults;
import ru.drom.gitgrep.service.GithubAuthenticator;
import ru.drom.gitgrep.util.RxAndroid;
import ru.drom.gitgrep.util.Utils;
import ru.drom.gitgrep.view.SaneDecor;
import ru.drom.gitgrep.view.SearchAdapter;
import ru.drom.gitgrep.view.SearchLayout;
import ru.drom.gitgrep.view.SearchLayoutManager;
import rx.Observable;
import rx.subjects.PublishSubject;

import static ru.drom.gitgrep.service.GithubAuthenticator.GITGREP_ACCOUNT_TYPE;
import static ru.drom.gitgrep.service.GithubAuthenticator.AUTH_TOKEN_ANY;

public final class MainActivity extends BaseActivity implements SearchLayout.SearchListener, MenuItemCompat.OnActionExpandListener {
    private static final String TAG = "MainActivity";

    private PublishSubject<ActivityEvent> lifecycle;
    private State state;
    private Context appContext;

    @BindView(R.id.act_main_list)
    RecyclerView searchesList;

    boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.act_main);

        ButterKnife.bind(this);

        appContext = getApplication();

        lifecycle = PublishSubject.create();

        final State last = (State) getLastNonConfigurationInstance();
        if (last == null) {
            state = new State();
            state.listAdapter = new SearchAdapter();
        } else {
            state = last;
        }

        final Resources res = getResources();
        if (state.bitmapCaches == null || !state.bitmapCaches.canReuse(res)) {
            state.bitmapCaches = new Bitmaps(res);
        }

        ensureAuthenticated();
    }

    private void ensureAuthenticated() {
        RxAndroid.requireAccountWithAuthToken(this, GITGREP_ACCOUNT_TYPE, AUTH_TOKEN_ANY)
                .subscribeOn(RxAndroid.bgLooperThread())
                .observeOn(RxAndroid.mainThread())
                .compose(RxLifecycle.bindUntilActivityEvent(lifecycle, ActivityEvent.DESTROY))
                .subscribe(this::proceedWithStartup, this::handleSignInError);
    }

    private void proceedWithStartup(Account account) {
        assert searchesList != null;

        ready = true;

        searchesList.setLayoutManager(new SearchLayoutManager(this));
        searchesList.addItemDecoration(new SaneDecor(this, LinearLayout.VERTICAL));
        searchesList.setAdapter(state.listAdapter);

        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_main_search);

        MenuItemCompat.setOnActionExpandListener(searchItem, this);

        final SearchLayout searchView = (SearchLayout) searchItem.getActionView();

        searchView.setSearchListener(this);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem searchItem = menu.findItem(R.id.menu_main_search);

        searchItem.setEnabled(ready);

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
                Utils.logError(this, error);
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

        super.onDestroy();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }

    @Override
    public void search(CharSequence query) {
        if (query.length() == 0) {
            state.listAdapter.setQuery(null);
        } else {
            state.listAdapter.setQuery(new MainActivity.RowFetcher(appContext, query.toString()));
        }
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        item.getActionView().requestFocus();

        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        return true;
    }

    private static class State {
        SearchAdapter listAdapter;
        Bitmaps bitmapCaches;
    }

    private static final class RowFetcher extends ContextWrapper implements SearchAdapter.RowFetcher, Callable<RepositoryResults> {
        private final String searchQuery;

        private int nextPage = 1;

        public RowFetcher(Context base, String searchQuery) {
            super(base);

            this.searchQuery = searchQuery;
        }

        @Override
        public Observable<RepositoryResults> next() {
            return Observable.fromCallable(this)
                    .subscribeOn(RxAndroid.bgLooperThread())
                    .observeOn(RxAndroid.mainThread())
                    .doOnNext(ok -> nextPage++);
        }

        @Override
        public RepositoryResults call() throws Exception {
            final GithubApi main = (GithubApi) getSystemService(GithubApi.SERVICE_API);

            return main.getRepositories(searchQuery, nextPage, 100);
        }
    }
}
