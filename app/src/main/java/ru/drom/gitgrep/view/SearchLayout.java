package ru.drom.gitgrep.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.trello.rxlifecycle.RxLifecycle;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import ru.drom.gitgrep.R;
import ru.drom.gitgrep.util.RxAndroid;
import rx.Observable;
import rx.subjects.PublishSubject;

public final class SearchLayout extends RelativeLayout implements TextWatcher {
    private final PublishSubject<?> textChanges = PublishSubject.create();
    private final PublishSubject<?> explicitInput = PublishSubject.create();

    @BindView(R.id.inc_search_et_main)
    EditText editor;

    private SearchListener searchListener;

    public SearchLayout(Context context) {
        super(context);

        onFinishInflate();
    }

    public SearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SearchLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ButterKnife.bind(this);

        editor.addTextChangedListener(this);

        final Observable<?> textHandler = textChanges.debounce(o -> {
            if (editor.getText().length() == 0) {
                return Observable.empty();
            }

            return Observable.empty()
                    .delay(2500, TimeUnit.MILLISECONDS, RxAndroid.mainThread())
                    .takeUntil(explicitInput);
        });

        textHandler
                .compose(RxLifecycle.bindView(this))
                .subscribe(next -> doSearch());
    }

    @OnEditorAction(R.id.inc_search_et_main)
    boolean editorAction(TextView tv, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN) {
            search();

            return true;
        }

        return false;
    }

    @OnClick(R.id.inc_search_btn_search)
    void search() {
        explicitInput.onNext(null);

        doSearch();
    }

    private void doSearch() {
        searchListener.search(editor.getText());
    }

    public void setSearchListener(SearchListener searchListener) {
        this.searchListener = searchListener;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        textChanges.onNext(null);
    }

    public interface SearchListener {
        void search(CharSequence query);
    }
}
