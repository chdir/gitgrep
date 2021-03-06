package ru.drom.gitgrep.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.carrotsearch.hppc.ObjectArrayList;
import com.trello.rxlifecycle.RxLifecycle;

import net.openhft.hashing.Access;
import net.openhft.hashing.LongHashFunction;

import java.io.IOException;

import butterknife.ButterKnife;
import ru.drom.gitgrep.Bitmaps;
import ru.drom.gitgrep.R;
import ru.drom.gitgrep.server.Owner;
import ru.drom.gitgrep.server.Repository;
import ru.drom.gitgrep.server.RepositoryResults;
import ru.drom.gitgrep.util.Utils;
import rx.Observable;
import rx.Subscription;

public final class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "SearchAdapter";

    private static final int STATE_EMPTY = 0;
    private static final int STATE_LOADING = 1;
    private static final int STATE_LOADED_SOME = 2;
    private static final int STATE_LOADED_ALL = 3;

    private static final int HARD_LIMIT = 1000;

    private static final Access<CharSequence> stringHasher = Access.toNativeCharSequence();

    private static final LongHashFunction xxHash = LongHashFunction.xx(System.nanoTime());

    private final ObjectArrayList<Repository> data = new ObjectArrayList<>();

    private RowFetcher fetcher;
    private Subscription ongoingSearch;
    private int count;

    private RecyclerView parent;
    private LayoutInflater inflater;
    private Bitmaps bitmaps;

    private int state;

    public SearchAdapter() {
        setHasStableIds(true);
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView parent) {
        super.onAttachedToRecyclerView(parent);

        final Context context = parent.getContext();

        this.parent = parent;
        this.bitmaps = Bitmaps.get(context);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        this.inflater = null;
        this.parent = null;

        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final ViewGroup layout;
        final RecyclerView.ViewHolder holder;

        switch (viewType) {
            case R.id.holder_progress:
                layout = (ViewGroup) inflater.inflate(R.layout.item_search_loading, parent, false);
                holder = new ProgressHolder(layout);
                break;
            default:
                layout = (ViewGroup) inflater.inflate(R.layout.item_search_result, parent, false);
                holder = new SearchHolder(layout);
                break;
        }

        ButterKnife.bind(holder, layout);

        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder template, int position) {
        switch (template.getItemViewType()) {
            case R.id.holder_progress:
                if (ongoingSearch != null) {
                    return;
                }

                triggerSearch();

                break;
            default:
                final SearchHolder holder = (SearchHolder) template;

                final Repository repository = data.get(position);

                holder.title.setText(repository.fullName);
                holder.text.setText(repository.description);

                final Owner owner = repository.owner;
                if (owner == null || owner.id == null) {
                    holder.setBitmap(null);
                    return;
                }

                final String key = owner.id;

                final Bitmaps.BitmapHolder cached = bitmaps.getCached(key);

                if (cached != null) {
                    holder.setBitmap(cached);
                    return;
                }

                holder.loading = bitmaps.load(key)
                        .compose(RxLifecycle.bindView(parent))
                        .subscribe(holder::setBitmap, err -> handleBitmapError(holder, key, err));
        }
    }

    private void triggerSearch() {
        ongoingSearch = fetcher.next()
                .finallyDo(() -> ongoingSearch = null)
                .subscribe(this::appendResults, this::handleError);
    }

    private void handleBitmapError(SearchHolder holder, String id, Throwable t) {
        holder.setBitmap(null);

        final String errMsg = "Failed to load image of " + id;

        if (t instanceof IOException) {
            // these can come in plenty, so don't log stacktrace for them
            Utils.logError(errMsg);
        } else {
            Utils.logError(errMsg, t);
        }
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder.getItemViewType() == R.id.holder_default) {
            final SearchHolder searchHolder = (SearchHolder) holder;

            final Bitmaps.BitmapHolder bh = searchHolder.bitmapHolder;

            searchHolder.setBitmap(null);

            if (bh != null) {
                bitmaps.release(bh);
            }
        }
    }

    @Override
    public int getItemCount() {
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == data.size()) {
            return R.id.holder_progress;
        }

        return R.id.holder_default;
    }

    @Override
    public long getItemId(int position) {
        if (position == data.size()) {
            return 0;
        }

        final Repository repository = data.get(position);

        return hash(repository.fullName) ^ xxHash.hashChars(repository.owner.id);
    }

    private static long hash(String str) {
        return xxHash.hash(str, stringHasher, 0, str.length());
    }

    public void setQuery(@Nullable RowFetcher fetcher) {
        this.fetcher = fetcher;

        data.elementsCount = 0;

        setCount(fetcher != null ? 1 : 0);

        if (ongoingSearch != null) {
            ongoingSearch.unsubscribe();

            triggerSearch();
        }
    }

    private void setCount(int count) {
        this.count = count;

        notifyDataSetChanged();
    }

    private void handleError(Throwable throwable) {
        setCount(data.size());
    }

    private void appendResults(RepositoryResults newResults) {
        if (newResults.totalCount == 0) {
            data.elementsCount = 0;
            setCount(0);
            return;
        }

        int total, overdue = 0;

        // Github limits us to 1000 results, but in case they ever lift the limitation,
        // let's double-check and enforce it ourselves
        if (newResults.totalCount > HARD_LIMIT) {
            total = HARD_LIMIT;

            final int sum = newResults.items.length + data.size();

            if (sum > HARD_LIMIT) {
                overdue = sum - HARD_LIMIT;
            }
        } else {
            total = newResults.totalCount;
        }

        data.ensureCapacity(total);

        data.add(newResults.items, 0, newResults.items.length - overdue);

        setCount(data.size() < total ? data.size() + 1 : data.size());
    }

    public interface RowFetcher {
        Observable<RepositoryResults> next();
    }
}
