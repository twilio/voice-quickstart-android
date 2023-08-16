package com.twilio.voice.quickstart;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.twilio.voice.quickstart.VoiceActivity.ACTION_DISCONNECT_CALL;
import static com.twilio.voice.quickstart.VoiceActivity.ACTION_DTMF_SEND;
import static com.twilio.voice.quickstart.VoiceActivity.DTMF;
import static com.twilio.voice.quickstart.VoiceActivity.OUTGOING_CALL_ADDRESS;

@RequiresApi(api = Build.VERSION_CODES.M)
public class VoiceConnectionService extends ConnectionService {
    private static final String TAG = "VoiceConnectionService";
    private static Connection activeConnection;

    public static Connection getConnection() {
        return activeConnection;
    }

    public static void releaseConnection() {
        if (null != activeConnection) {
            activeConnection.destroy();
            activeConnection = null;
        }
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Connection incomingCallConnection = createConnection(request);
        incomingCallConnection.setRinging();
        return incomingCallConnection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {
        Connection outgoingCallConnection = createConnection(request);
        outgoingCallConnection.setDialing();
        return outgoingCallConnection;
    }

    private Connection createConnection(ConnectionRequest request) {
        activeConnection = new Connection() {

            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.post(() -> sendCallRequestToActivity(Constants.ACTION_OUTGOING_CALL));
                }
            }

            @Override
            public void onCallAudioStateChanged(CallAudioState state) {
                Log.d(TAG, "onCallAudioStateChanged called, current state is " + state);
            }

            @Override
            public void onPlayDtmfTone(char c) {
                Log.d(TAG, "onPlayDtmfTone called with DTMF " + c);
                Bundle extras = new Bundle();
                extras.putString(DTMF, Character.toString(c));
                activeConnection.setExtras(extras);
                final Handler handler = new Handler();
                handler.post(() -> sendCallRequestToActivity(ACTION_DTMF_SEND));
            }

            @Override
            public void onDisconnect() {
                super.onDisconnect();
                activeConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                releaseConnection();
                final Handler handler = new Handler();
                handler.post(() -> sendCallRequestToActivity(ACTION_DISCONNECT_CALL));
            }

            @Override
            public void onSeparate() {
                super.onSeparate();
            }

            @Override
            public void onAbort() {
                super.onAbort();
                activeConnection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                releaseConnection();
            }

            @Override
            public void onAnswer() {
                super.onAnswer();
                // todo
            }

            @Override
            public void onReject() {
                super.onReject();
                // todo
                activeConnection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                releaseConnection();
            }

            @Override
            public void onPostDialContinue(boolean proceed) {
                super.onPostDialContinue(true);
            }
        };
        // setup the origin of the caller
        final Uri recipient = request.getExtras().getParcelable(Constants.OUTGOING_CALL_RECIPIENT);
        if (null != recipient) {
            activeConnection.setAddress(recipient, TelecomManager.PRESENTATION_ALLOWED);
        } else {
            activeConnection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        }
        // self managed isn't available before version O
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeConnection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        // set mute capability (for DTMF support?)
        activeConnection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        activeConnection.setDialing();
        return activeConnection;
    }

    /*
     * Send call request to the VoiceConnectionServiceActivity
     */
    private void sendCallRequestToActivity(String action) {
        Intent intent = new Intent(action);
        Bundle extras = new Bundle();
        switch (action) {
            case Constants.ACTION_OUTGOING_CALL:
                Uri address = activeConnection.getAddress();
                extras.putParcelable(Constants.OUTGOING_CALL_RECIPIENT, address);
                intent.putExtras(extras);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DISCONNECT_CALL:
                extras.putInt("Reason", DisconnectCause.LOCAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtras(extras);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DTMF_SEND:
                String d = activeConnection.getExtras().getString(DTMF);
                extras.putString(DTMF, activeConnection.getExtras().getString(DTMF));
                intent.putExtras(extras);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            default:
                break;
        }
    }
}
