package ru.drom.gitgrep.view;

import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindDrawable;
import butterknife.BindView;
import ru.drom.gitgrep.Bitmaps;
import ru.drom.gitgrep.R;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

public final class SearchHolder extends RecyclerView.ViewHolder {
    @BindView(android.R.id.text2)
    public TextView text;

    @BindView(android.R.id.text1)
    public TextView title;

    @BindView(android.R.id.icon)
    public ImageView image;

    @BindDrawable(R.drawable.ic_error_outline_black_24dp)
    Drawable errorIcon;

    public Bitmaps.BitmapHolder bitmapHolder;
    public Subscription loading;

    public SearchHolder(View itemView) {
        super(itemView);
    }

    public void setBitmap(Bitmaps.BitmapHolder loaded) {
        if (loading != null) {
            loading.unsubscribe();
            loading = null;
        }

        bitmapHolder = loaded;

        if (loaded == null) {
            image.setImageDrawable(null);

            return;
        }

        image.setImageBitmap(loaded.bitmap);

        loaded.state |= Bitmaps.BitmapHolder.DRAWN;
    }

    public void showError() {
        image.setImageDrawable(errorIcon);
    }
}
