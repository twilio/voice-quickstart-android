package com.twilio.examplecustomlogger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.Voice;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomLoggerActivity extends AppCompatActivity {

    private static final String TAG = "CustomLoggerActivity";
    private static final int MIC_PERMISSION_REQUEST_CODE = 1;
    // Showing the whole file would be slow to render, so only its end is shown.
    private static final int MAX_LOG_LINES = 300;

    private String accessToken = "PASTE_YOUR_ACCESS_TOKEN_HERE";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText calleeEditText;
    private Button callButton;
    private Button hangupButton;
    private TextView logView;
    private ScrollView logScroll;
    private File logFile;
    private Call activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_logger);

        calleeEditText = findViewById(R.id.callee);
        callButton = findViewById(R.id.call_button);
        hangupButton = findViewById(R.id.hangup_button);
        logView = findViewById(R.id.log_view);
        logScroll = findViewById(R.id.log_scroll);
        logFile = ((CustomLoggerApplication) getApplication()).getLogFile();

        ((TextView) findViewById(R.id.log_file_path))
                .setText(getString(R.string.log_file, logFile.getAbsolutePath()));

        callButton.setOnClickListener(v -> makeCall());
        hangupButton.setOnClickListener(
                v -> {
                    if (activeCall != null) {
                        activeCall.disconnect();
                    }
                });
        findViewById(R.id.refresh_log_button).setOnClickListener(v -> refreshLog());

        if (!checkPermissionForMicrophone()) {
            ActivityCompat.requestPermissions(
                    this, new String[] {Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST_CODE);
        }
        refreshLog();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void makeCall() {
        if (!checkPermissionForMicrophone()) {
            showMessage("Microphone permissions needed. Please allow in your application settings.");
            return;
        }
        HashMap<String, String> params = new HashMap<>();
        String callee = calleeEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(callee)) {
            params.put("to", callee);
        }
        ConnectOptions connectOptions =
                new ConnectOptions.Builder(accessToken).params(params).build();
        activeCall = Voice.connect(this, connectOptions, callListener());
        callButton.setEnabled(false);
        hangupButton.setEnabled(true);
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onRinging(@NonNull Call call) {
                Log.d(TAG, "Ringing");
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                Log.e(TAG, "Connect failure: " + error.getErrorCode() + " " + error.getMessage());
                showMessage(
                        String.format(
                                Locale.US,
                                "Call Error: %d, %s",
                                error.getErrorCode(),
                                error.getMessage()));
                resetUI();
            }

            @Override
            public void onConnected(@NonNull Call call) {
                Log.d(TAG, "Connected " + call.getSid());
                activeCall = call;
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                Log.d(TAG, "onReconnecting");
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                Log.d(TAG, "onReconnected");
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                Log.d(TAG, "Disconnected");
                if (error != null) {
                    showMessage(
                            String.format(
                                    Locale.US,
                                    "Call Error: %d, %s",
                                    error.getErrorCode(),
                                    error.getMessage()));
                }
                resetUI();
            }
        };
    }

    private void resetUI() {
        activeCall = null;
        callButton.setEnabled(true);
        hangupButton.setEnabled(false);
        refreshLog();
    }

    /** Reads the end of the log file off the main thread and shows it. */
    private void refreshLog() {
        executor.execute(
                () -> {
                    final String text = readTail(logFile);
                    mainHandler.post(
                            () -> {
                                logView.setText(text.isEmpty() ? getString(R.string.log_empty) : text);
                                logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
                            });
                });
    }

    private static String readTail(File file) {
        Deque<String> lines = new ArrayDeque<>(MAX_LOG_LINES + 1);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() == MAX_LOG_LINES) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        } catch (IOException e) {
            // A missing file just means nothing has been logged yet.
            return "";
        }
        return TextUtils.join("\n", lines);
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private boolean checkPermissionForMicrophone() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            showMessage("Microphone permissions needed. Please allow in your application settings.");
        }
    }
}
