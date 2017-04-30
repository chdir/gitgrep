package ru.drom.gitgrep.view;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import ru.drom.gitgrep.Bitmaps;
import rx.Subscription;

public final class SearchHolder extends RecyclerView.ViewHolder {
    @BindView(android.R.id.text2)
    public TextView text;

    @BindView(android.R.id.text1)
    public TextView title;

    @BindView(android.R.id.icon)
    public ImageView image;

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
}
