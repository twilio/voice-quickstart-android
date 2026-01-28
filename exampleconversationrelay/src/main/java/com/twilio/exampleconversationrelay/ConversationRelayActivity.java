package com.twilio.exampleconversationrelay;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.Voice;

import java.util.HashMap;
import java.util.Map;


public class ConversationRelayActivity extends AppCompatActivity implements Call.Listener {
    static final String accessToken = "PASTE_TOKEN_HERE";
    static final String conversationRelayUrl = "your server public URL";

    private enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }
    private final StringBuffer logBuffer = new StringBuffer();
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private Call activeCall = null;
    private static final String[] permission_list = { Manifest.permission.RECORD_AUDIO };
    private static final int PERMISSIONS_ALL = 100;

    // UI elements
    private EditText log_text;
    private Button connection_button;

    public void onConnectionButtonPressed(View view) {
        switch (state) {
            case DISCONNECTED:
                // connect to conversation relay
                state = ConnectionState.CONNECTING;
                Map<String, String> params = new HashMap<>();
                params.put("ConversationRelayUrl", conversationRelayUrl);
                ConnectOptions options =
                        new ConnectOptions.Builder(accessToken).params(params).build();
                activeCall = Voice.connect(this, options, this);
                break;
            case CONNECTED:
            case CONNECTING:
                state = ConnectionState.DISCONNECTING;
                activeCall.disconnect();
                break;
        }
        // update ui
        connection_button.setText(
                ConnectionState.DISCONNECTED == state
                        ? R.string.connect_button_text : R.string.disconnect_button_text);
    }

    @Override
    public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
        logMessage("Connection failed: " + callException.getMessage());
        state = ConnectionState.DISCONNECTED;
        activeCall = null;
        // update ui
        connection_button.setText(R.string.connect_button_text);
    }

    @Override
    public void onRinging(@NonNull Call call) {
        logMessage("Connection ringing");
    }

    @Override
    public void onConnected(@NonNull Call call) {
        logMessage("Connection connected");
        state = ConnectionState.CONNECTED;
    }

    @Override
    public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
        logMessage("Connection reconnecting: " + callException.getMessage());
    }

    @Override
    public void onReconnected(@NonNull Call call) {
        logMessage("Connection reconnected");
    }

    @Override
    public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
        logMessage("Connection disconnected" +
                (callException != null ? ": " + callException.getMessage() : ""));
        state = ConnectionState.DISCONNECTED;
        activeCall = null;
        // update ui
        connection_button.setText(R.string.connect_button_text);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_ALL) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PERMISSION_GRANTED) {
                    logMessage("Required permissions were denied: " + permissions[i]);
                }
            }
        }
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // wire up UI
        setContentView(R.layout.conversation_relay_activity);
        log_text = findViewById(R.id.log_text);
        connection_button = findViewById(R.id.connect_button);

        // Ensure required permissions are enabled
        if (!hasPermissions(this, permission_list)) {
            ActivityCompat.requestPermissions(this, permission_list, PERMISSIONS_ALL);
        }
    }

    private boolean hasPermissions(Context context, final String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(context, permission)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void logMessage(String message) {
        logBuffer.append(message).append("\n");
        log_text.setText(logBuffer.toString());
    }
}
