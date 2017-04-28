package ru.drom.gitgrep.util;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.codegist.crest.CRestException;

import java.util.Calendar;
import java.util.GregorianCalendar;

import rx.functions.Action1;

public final class Utils {
    private static final CachedCalendar calendarCache = new CachedCalendar();

    private Utils() {}

    public static boolean isWithinTokenReuseWindow(long currentTime, long lastTokenOveruseTime) {
        if (currentTime < lastTokenOveruseTime) {
            // the time has gone back, assume we are cool
            return true;
        }

        final Calendar calendar = calendarCache.get();
        calendar.setTimeInMillis(lastTokenOveruseTime);
        calendar.add(Calendar.MINUTE, 1);
        return currentTime > calendar.getTimeInMillis();
    }

    private static Toast lastToast;

    public static void toast(Context context, CharSequence text) {
        if (lastToast != null) {
            lastToast.cancel();
        }

        final Context appContext = context.getApplicationContext();

        lastToast = Toast.makeText(appContext, text, Toast.LENGTH_SHORT);
        lastToast.show();
    }

    public static void runOnMain(Context context, Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            runnable.run();
        } else {
            HandlerScheduler.HANDLER.post(runnable);
        }
    }

    public static void logError(Context context, Throwable t) {
        final String msg = t.getClass().getCanonicalName() + ": " + t.getMessage();

        Log.e("!!!", msg, t);

        toast(context, msg);
    }

    public static void ignore(Object... ignored) {
    }

    public static RuntimeException wrap(Exception t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }

        return new RuntimeException(t.getCause());
    }

    private static final class CachedCalendar extends ThreadLocal<Calendar> {
        @Override
        protected Calendar initialValue() {
            return new GregorianCalendar();
        }
    };
}
