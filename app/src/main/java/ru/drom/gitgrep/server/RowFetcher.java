package ru.drom.gitgrep.server;

import android.content.Context;
import android.content.ContextWrapper;

import java.util.concurrent.Callable;

import ru.drom.gitgrep.util.RxAndroid;
import ru.drom.gitgrep.view.SearchAdapter;
import rx.Observable;

public final class RowFetcher extends ContextWrapper implements SearchAdapter.RowFetcher, Callable<RepositoryResults> {
    private static final int PAGE_SIZE = 100;

    private final String searchQuery;

    private int nextPage = 1;

    private OnError errorHandler;

    public RowFetcher(Context base, String searchQuery) {
        super(base);

        this.searchQuery = searchQuery;
    }

    @Override
    public Observable<RepositoryResults> next() {
        return Observable.fromCallable(this)
                .subscribeOn(RxAndroid.bgLooperThread())
                .observeOn(RxAndroid.mainThread())
                .doOnError(this::handleSearchError)
                .doOnNext(this::handleSuccess);
    }

    private void handleSuccess(RepositoryResults repositoryResults) {
        nextPage++;

        if (errorHandler != null) errorHandler.onSuccess();
    }

    private void handleSearchError(Throwable throwable) {
        if (errorHandler != null) errorHandler.onError(throwable);
    }

    public void setErrorHandler(OnError errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public RepositoryResults call() throws Exception {
        final GithubApi main = (GithubApi) getSystemService(GithubApi.SERVICE_API);

        return main.getRepositories(searchQuery, nextPage, PAGE_SIZE);
    }

    public interface OnError {
        void onSuccess();

        void onError(Throwable throwable);
    }
}
