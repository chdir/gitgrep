package ru.drom.gitgrep.util;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.codegist.crest.CRestException;

import java.util.Calendar;
import java.util.GregorianCalendar;

import rx.functions.Action1;

public final class Utils {
    private static final String TAG = "GitGrep";

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

    public static void logError(String message) {
        Log.e(TAG, message);
    }

    public static void logError(Context context, Throwable t) {
        logError(t);

        toast(context, massageIntoSensibleForm(getMessage(t)));
    }

    public static void logError(Throwable t) {
        logError(getMessage(t), t);
    }

    public static void logError(String custom, Throwable t) {
        Log.e(TAG, custom, t);
    }

    private static String getMessage(Throwable t) {
        String message = t.getMessage();

        if (message == null) {
            final Class<?> clazz = t.getClass();

            message = clazz.getCanonicalName();

            if (message == null) {
                message = clazz.getName();
            }
        }

        return message;
    }

    public static String massageIntoSensibleForm(@Nullable String errText) {
        // https://stackoverflow.com/questions/21165802
        final String trimmedText = errText.replaceAll("(?m)(^ *| +(?= |$))", "")
                .replaceAll("(?m)^$([\r\n]+?)(^$[\r\n]+?^)+", "$1");

        if (!TextUtils.isEmpty(trimmedText)) {

            return trimmedText.length() > 300
                    ? '…' + trimmedText.substring(trimmedText.length() - 299, trimmedText.length())
                    : trimmedText;
        }

        return "Unknown error";
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
