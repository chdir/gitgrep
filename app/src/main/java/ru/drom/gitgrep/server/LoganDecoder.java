package ru.drom.gitgrep.server;

import android.support.annotation.Nullable;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.carrotsearch.hppc.LongArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import junit.framework.Assert;

import org.codegist.crest.serializer.Deserializer;
import org.codegist.crest.serializer.Serializer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public final class LoganDecoder implements Deserializer, Serializer {
    private static final String TAG = "LoganDecoder";

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T> T deserialize(Class<T> type, Type genericType, InputStream stream, Charset charset) throws Exception {
        final long nanoStarted = System.nanoTime();

        try (Closeable c = stream) {
            if (type == void.class) {
                // do not consume the stream just pass through
                return null;
            } else if (type.isArray()) {
                return parseArray(type.getComponentType(), stream);
            } else if (type.isAssignableFrom(List.class)) {
                final ParameterizedType listType = (ParameterizedType) genericType;

                final Class listElementType = (Class) (listType.getActualTypeArguments()[0]);

                final List<?> aList = LoganSquare.parseList(stream, listElementType);

                return (T) aList;
            } else if (type.isAssignableFrom(Map.class)) {
                final ParameterizedType listType = (ParameterizedType) genericType;

                final Type[] args = listType.getActualTypeArguments();
                final Class mapKeyType = (Class) args[0];
                final Class mapValueType = (Class) args[1];

                Assert.assertEquals(mapKeyType, String.class);
                final Map<String, ?> aMap = LoganSquare.parseMap(stream, mapValueType);

                return (T) aMap;
            } else {
                return LoganSquare.parse(stream, type);
            }
        } finally {
            final long nanoFinished = System.nanoTime();

            Log.i(TAG, "Decoded " + type + " within " + (nanoFinished - nanoStarted) / 1000000 + " milliseconds");
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> R parseArray(Class<?> component, InputStream stream) throws IOException {
        return component.isPrimitive()
                ? parsePrimitiveArray(component, stream)
                : (R) parseDtoArray(component, stream);
    }

    @SuppressWarnings("unchecked")
    private static <O> O[] parseDtoArray(Class<O> component, InputStream stream) throws IOException {
        final List<O> aList = LoganSquare.parseList(stream, component);

        return aList.toArray((O[]) Array.newInstance(component, aList.size()));
    }

    private static <P> P parsePrimitiveArray(Class<?> primitiveType, InputStream stream) throws IOException {
        try (JsonParser parser = LoganSquare.JSON_FACTORY.createParser(stream)) {
            return parsePrimitiveArray(primitiveType, parser);
        }
    }

    private static final long[] EMPTY_LONG = new long[0];

    private static <P> P parsePrimitiveArray(Class<?> primitiveType, JsonParser streamParser) throws IOException {
        switch (primitiveType.getCanonicalName()) {
            case "long":
                final LongArrayList longs = new LongArrayList(10);

                if (streamParser.getCurrentToken() == null) {
                    streamParser.nextToken();
                }

                if (streamParser.getCurrentToken() != JsonToken.START_ARRAY) {
                    streamParser.skipChildren();
                    return (P) EMPTY_LONG;
                }

                while (streamParser.nextToken() != JsonToken.END_ARRAY) {
                    longs.add(streamParser.getLongValue());
                }

                return (P) longs.toArray();
            default:
                throw new UnsupportedOperationException("Type " + primitiveType + " not supported yet!");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(Object value, Charset charset, OutputStream out) throws Exception {
        final Class<?> clazz = value.getClass();
        if (clazz.isArray()) {
            LoganSquare.serialize(Arrays.asList((Object[]) value), out, (Class<Object>) clazz.getComponentType());
        } else {

            LoganSquare.serialize(value, out);
        }
    }
}