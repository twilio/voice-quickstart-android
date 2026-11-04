package com.twilio.examplecustomlogger;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
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
    private CallInvite callInvite;
    private View incomingPanel;
    private TextView incomingText;

    private final BroadcastReceiver cancelReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    hideIncoming();
                }
            };

    private final RegistrationListener registrationListener =
            new RegistrationListener() {
                @Override
                public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
                    Log.d(TAG, "Registered for incoming calls");
                }

                @Override
                public void onError(
                        @NonNull RegistrationException error,
                        @NonNull String accessToken,
                        @NonNull String fcmToken) {
                    Log.e(TAG, "Registration failed: " + error.getErrorCode() + " " + error.getMessage());
                    showMessage("Registration Error: " + error.getErrorCode() + ", " + error.getMessage());
                }
            };

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

        incomingPanel = findViewById(R.id.incoming_panel);
        incomingText = findViewById(R.id.incoming_text);
        findViewById(R.id.accept_button).setOnClickListener(v -> acceptInvite());
        findViewById(R.id.reject_button).setOnClickListener(v -> rejectInvite());
        ContextCompat.registerReceiver(
                this,
                cancelReceiver,
                new IntentFilter(IncomingCallService.ACTION_CANCELLED),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        List<String> permissions = new ArrayList<>();
        if (!checkPermissionForMicrophone()) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, permissions.toArray(new String[0]), MIC_PERMISSION_REQUEST_CODE);
        }

        registerForIncomingCalls();
        handleIntent(getIntent());
        refreshLog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(cancelReceiver);
        executor.shutdown();
        super.onDestroy();
    }

    private void registerForIncomingCalls() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.w(TAG, "Firebase is not configured (no google-services.json): incoming calls disabled");
            return;
        }
        FirebaseMessaging.getInstance()
                .getToken()
                .addOnCompleteListener(
                        task -> {
                            if (!task.isSuccessful()) {
                                Log.e(TAG, "FCM token retrieval failed", task.getException());
                                return;
                            }
                            Voice.register(
                                    accessToken,
                                    Voice.RegistrationChannel.FCM,
                                    task.getResult(),
                                    registrationListener);
                        });
    }

    private void handleIntent(Intent intent) {
        CallInvite invite = IncomingCallService.getInvite(intent);
        if (invite != null) {
            intent.removeExtra(IncomingCallService.EXTRA_CALL_INVITE);
            callInvite = invite;
            incomingText.setText(getString(R.string.incoming_call, invite.getFrom()));
            incomingPanel.setVisibility(View.VISIBLE);
            if (intent.getBooleanExtra(IncomingCallService.EXTRA_ACCEPT, false)) {
                intent.removeExtra(IncomingCallService.EXTRA_ACCEPT);
                acceptInvite();
            }
        }
    }

    private void acceptInvite() {
        if (callInvite == null) {
            return;
        }
        if (!ensureMicrophone()) {
            return;
        }
        activeCall = callInvite.accept(this, callListener());
        hideIncoming();
        callButton.setEnabled(false);
        hangupButton.setVisibility(View.VISIBLE);
    }

    private void rejectInvite() {
        if (callInvite != null) {
            callInvite.reject(this);
        }
        hideIncoming();
    }

    private void hideIncoming() {
        callInvite = null;
        incomingPanel.setVisibility(View.GONE);
        getSystemService(NotificationManager.class).cancel(IncomingCallService.NOTIFICATION_ID);
    }

    private void makeCall() {
        if (!ensureMicrophone()) {
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
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onRinging(@NonNull Call call) {
                hangupButton.setVisibility(View.VISIBLE);
                Log.d(TAG, "Ringing");
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                Log.e(TAG, "Connect failure: " + error.getErrorCode() + " " + error.getMessage());
                showCallError(error);
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
                    showCallError(error);
                }
                resetUI();
            }
        };
    }

    private void resetUI() {
        activeCall = null;
        callButton.setEnabled(true);
        hangupButton.setVisibility(View.GONE);
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

    private boolean ensureMicrophone() {
        if (checkPermissionForMicrophone()) {
            return true;
        }
        showMessage(getString(R.string.microphone_permission_needed));
        return false;
    }

    private void showCallError(CallException error) {
        showMessage(
                String.format(
                        Locale.US, "Call Error: %d, %s", error.getErrorCode(), error.getMessage()));
    }

    private boolean checkPermissionForMicrophone() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MIC_PERMISSION_REQUEST_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])
                    && grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                showMessage(getString(R.string.microphone_permission_needed));
            }
        }
    }
}
