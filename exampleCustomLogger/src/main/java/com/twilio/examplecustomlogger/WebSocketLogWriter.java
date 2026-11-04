package com.twilio.examplecustomlogger;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketLogWriter implements LogWriter {
    private static final String TAG = "WebSocketLogWriter";
    private static final int MAX_PENDING_LINES = 1000;
    private static final long RECONNECT_DELAY_MS = 3000;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String url;
    private final Deque<String> pending = new ArrayDeque<>();
    private WebSocket socket;
    private boolean connecting;

    public WebSocketLogWriter(@NonNull String url) {
        this.url = url;
        connect();
    }

    @Override
    public synchronized void write(@NonNull String line) {
        if (socket != null) {
            socket.send(line);
            return;
        }
        if (pending.size() == MAX_PENDING_LINES) {
            pending.removeFirst();
        }
        pending.addLast(line);
    }

    private synchronized void connect() {
        if (connecting) {
            return;
        }
        connecting = true;
        client.newWebSocket(
                new Request.Builder().url(url).build(),
                new WebSocketListener() {
                    @Override
                    public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                        synchronized (WebSocketLogWriter.this) {
                            connecting = false;
                            socket = ws;
                            String line;
                            while ((line = pending.pollFirst()) != null) {
                                ws.send(line);
                            }
                        }
                    }

                    @Override
                    public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                        disconnected(ws);
                    }

                    @Override
                    public void onFailure(
                            @NonNull WebSocket ws, @NonNull Throwable t, Response response) {
                        Log.e(TAG, "WebSocket failure: " + t);
                        disconnected(ws);
                    }
                });
    }

    private synchronized void disconnected(WebSocket ws) {
        if (socket == ws || socket == null) {
            socket = null;
            connecting = false;
            handler.postDelayed(this::connect, RECONNECT_DELAY_MS);
        }
    }
}
