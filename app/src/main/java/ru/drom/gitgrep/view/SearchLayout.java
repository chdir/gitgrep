package ru.drom.gitgrep.view;

import android.content.Context;
import android.support.v7.view.CollapsibleActionView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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

public final class SearchLayout extends RelativeLayout implements CollapsibleActionView, TextWatcher {
    private final PublishSubject<String> textChanges = PublishSubject.create();
    private final PublishSubject<?> explicitInput = PublishSubject.create();

    @BindView(R.id.inc_search_et_main)
    EditText editor;

    private InputMethodManager imeMgr;
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

        final Context context = getContext();

        imeMgr = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);

        ButterKnife.bind(this);

        editor.addTextChangedListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // subscribe to changes only after onRestoreInstanceState is called
        getHandler().post(this::subscribeToChanges);
    }

    private void subscribeToChanges() {
        final Observable<?> textHandler = textChanges.debounce(o -> {
            if (editor.getText().length() == 0) {
                return Observable.empty();
            }

            return Observable.empty()
                    .delay(2500, TimeUnit.MILLISECONDS, RxAndroid.mainThread())
                    .takeUntil(explicitInput);
        });

        textHandler
                .distinctUntilChanged()
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
        textChanges.onNext(s.toString());
    }

    @Override
    public void onActionViewExpanded() {
        editor.requestFocus();

        imeMgr.showSoftInput(editor, 0);
    }

    @Override
    public void onActionViewCollapsed() {
        if (isAttachedToWindow() && editor.isInputMethodTarget()) {
            imeMgr.hideSoftInputFromWindow(editor.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public interface SearchListener {
        void search(CharSequence query);
    }
}
