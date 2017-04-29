package ru.drom.gitgrep;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.CircularArray;
import android.util.Log;
import android.util.LruCache;

import com.carrotsearch.hppc.CharArrayList;

import java.io.InputStream;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import ru.drom.gitgrep.util.RxAndroid;
import rx.Observable;

public final class Bitmaps {
    private static final String TAG = "Bitmaps";

    private static final int MAX_IMAGE_FILE_SIZE = 1024 * 1024 * 20;

    private static final int GITHUB_SIZE = 420;

    private static final Caches threadCaches = new Caches();

    private final ImageCache lruCache = new ImageCache();

    private final GrowingBitmapPool bitmapPool = new GrowingBitmapPool();

    private final int bitmapSize;

    public Bitmaps(Resources resources) {
        this.bitmapSize = resources.getDimensionPixelSize(R.dimen.bitmapSize);
    }

    public boolean canReuse(Resources resources) {
        return bitmapSize == resources.getDimensionPixelSize(R.dimen.bitmapSize);
    }

    public @Nullable BitmapHolder getCached(String userId) {
        return lruCache.get(userId);
    }

    public @NonNull Observable<BitmapHolder> load(String userId) {
        final Observable<BitmapHolder> result = Observable.fromCallable(() -> {
            final ThreadData data = threadCaches.get();

            final StringMaker uri = data.builder;

            uri.clear();
            uri.append("https://avatars.githubusercontent.com/u/");
            uri.append(userId);
            uri.append("?v=3&s=");
            uri.append(bitmapSize);

            final URL url = new URL(data.builder.toString());

            final URLConnection connection = url.openConnection();

            connection.setRequestProperty("User-Agent", "GitGrep");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(true);

            try (InputStream stream = connection.getInputStream()) {
                final int size = connection.getContentLength();

                if (size > MAX_IMAGE_FILE_SIZE) {
                    Log.i(TAG, "Image for " + userId + " is larger than maximum allowed size (" + size + " bytes)");
                    return null;
                }

                data.opts.inBitmap = data.reusable;

                if (Thread.interrupted()) return null;

                Bitmap bitmap = BitmapFactory.decodeStream(stream, null, data.opts);

                if (bitmap == null) {
                    return null;
                }

                if (data.reusable == null) {
                    final int w = Math.max(GITHUB_SIZE, bitmap.getWidth());
                    final int h = Math.max(GITHUB_SIZE, bitmap.getHeight());
                    data.reusable = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                }

                Bitmap scaled = bitmapPool.acquire();

                if (scaled == null) {
                    scaled = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
                }

                data.scaleInPlace(bitmap, scaled, bitmapSize);

                final BitmapHolder holder = new BitmapHolder();

                holder.bitmap = scaled;
                holder.state |= BitmapHolder.CACHED;

                lruCache.put(userId, holder);

                return holder;
            } catch (UnknownHostException | SSLException | SocketException uhe) {
                throw uhe;
            } catch (Throwable t) {
                if (data.reusable != null) {
                    data.reusable.recycle();
                    data.reusable = null;
                }

                throw new RuntimeException("Decoding failed for " + userId, t);
            }
        });

        return result
                .subscribeOn(RxAndroid.bgLooperThread())
                .observeOn(RxAndroid.mainThread());
    }

    public void release(@NonNull BitmapHolder bitmap) {
        bitmap.state &= ~BitmapHolder.DRAWN;

        if (bitmap.state == 0 && !bitmapPool.release(bitmap.bitmap)) {
            bitmap.bitmap.recycle();
        }
    }

    private static final class Caches extends ThreadLocal<ThreadData> {
        @Override
        protected ThreadData initialValue() {
            return new ThreadData();
        }
    }

    private static final class ThreadData {
        final StringMaker builder = new StringMaker();

        final byte[] array = new byte[1024 * 64];

        final Canvas scalingCanvas = new Canvas();

        final Paint scalingPaint = new Paint(Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        final Matrix matrix = new Matrix();

        final RectF destRect = new RectF();

        final RectF imageRect = new RectF();

        private void scaleInPlace(Bitmap source, Bitmap target, int size) {
            imageRect.right = source.getWidth();
            imageRect.bottom = source.getHeight();

            destRect.right = size;
            destRect.bottom = size;

            matrix.setRectToRect(imageRect, destRect, Matrix.ScaleToFit.CENTER);

            scalingCanvas.setBitmap(target);
            scalingCanvas.drawBitmap(source, matrix, scalingPaint);
        }

        Bitmap reusable;

        private final BitmapFactory.Options opts = new BitmapFactory.Options();

        { opts.inTempStorage = array; opts.inScaled = false; opts.inSampleSize = 1; opts.inMutable = true; }
    }

    private static final class StringMaker extends CharArrayList
    {
        public void append(String str) {
            ensureBufferSpace(str.length());

            str.getChars(0, str.length(), buffer, elementsCount);

            elementsCount += str.length();
        }

        public void append(int i) {
            append(String.valueOf(i));
        }

        public void clear() {
            elementsCount = 0;
        }

        public String toString() {
            return new String(buffer, 0, elementsCount);
        }
    }

    private final class ImageCache extends LruCache<String, BitmapHolder> {
        public ImageCache() {
            super(1024 * 1024 * 20);
        }

        @Override
        protected int sizeOf(String key, BitmapHolder value) {
            return value.bitmap.getAllocationByteCount();
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, BitmapHolder oldValue, BitmapHolder newValue) {
            oldValue.state &= ~BitmapHolder.CACHED;

            if (oldValue.state == 0 && !bitmapPool.release(oldValue.bitmap)) {
                oldValue.bitmap.recycle();
            }
        }
    };

    private final class GrowingBitmapPool {
        private static final int MAX = 100;

        private final CircularArray<Bitmap> bitmaps = new CircularArray<>(5);

        public Bitmap acquire() {
            synchronized (this) {
                if (bitmaps.size() == 0) {
                    return null;
                }

                return bitmaps.popLast();
            }
        }

        public boolean release(Bitmap element) {
            synchronized (this) {
                if (bitmaps.size() >= MAX) return false;

                bitmaps.addLast(element);
            }

            return true;
        }
    }

    public static final class BitmapHolder {
        public static final int DRAWN  = 0b10;
        public static final int CACHED = 0b01;

        public Bitmap bitmap;
        public int state;
    }

    public static final String SERVICE_BITMAPS = "ru.dom.gitgrep.services.bitmaps";

    public static Bitmaps get(Context context) {
        final Bitmaps scoped = (Bitmaps) context.getSystemService(SERVICE_BITMAPS);

        if (scoped != null) {
            return scoped;
        }

        final Context appContext = context.getApplicationContext();

        final Bitmaps global = (Bitmaps) appContext.getSystemService(SERVICE_BITMAPS);

        if (global != null) {
            return global;
        }

        return null;
    }
}
