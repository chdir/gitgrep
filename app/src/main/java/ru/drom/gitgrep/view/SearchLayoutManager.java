package ru.drom.gitgrep.view;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;

public final class SearchLayoutManager extends LinearLayoutManager {
    private final OrientationHelper orientationHelper;

    public SearchLayoutManager(Context context) {
        super(context);

        this.orientationHelper = OrientationHelper.createVerticalHelper(this);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        super.onLayoutChildren(recycler, state);

        if (!state.isPreLayout()) {
            orientationHelper.onLayoutComplete();
        }
    }

    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        return orientationHelper.getTotalSpace();
    }
}
