package com.twilio.voice.quickstart;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static com.twilio.voice.quickstart.VoiceActivity.ACTION_DISCONNECT_CALL;
import static com.twilio.voice.quickstart.VoiceActivity.ACTION_DTMF_SEND;
import static com.twilio.voice.quickstart.VoiceActivity.ACTION_OUTGOING_CALL;
import static com.twilio.voice.quickstart.VoiceActivity.DTMF;

import com.twilio.voice.CallInvite;

@RequiresApi(api = Build.VERSION_CODES.M)
public class VoiceConnectionService extends ConnectionService {
    private static final String TAG = "VoiceConnectionService";

    private static Connection connection;

    public static Connection getConnection() {
        return connection;
    }

    public static void releaseConnection() {
        connection.destroy();
        connection = null;
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

    private Connection createConnection(final ConnectionRequest request) {
        connection = new Connection() {

            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.post(() -> sendCallRequestToActivity(ACTION_OUTGOING_CALL));
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
                connection.setExtras(extras);
                final Handler handler = new Handler();
                handler.post(() -> sendCallRequestToActivity(ACTION_DTMF_SEND));
            }

            @Override
            public void onDisconnect() {
                super.onDisconnect();
                connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                connection.destroy();
                connection = null;
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
                connection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                connection.destroy();
                connection = null;
            }

            @Override
            public void onAnswer() {
                super.onAnswer();
                connection.setActive();
                // extract call invite
                Bundle inviteBundle = request.getExtras().getBundle(Constants.INCOMING_CALL_INVITE);
                inviteBundle.setClassLoader(CallInvite.class.getClassLoader());
                CallInvite invite = inviteBundle.getParcelable(Constants.INCOMING_CALL_INVITE);
                // notify activity
                Intent acceptIntent = new Intent(getApplicationContext(), NotificationProxyActivity.class);
                acceptIntent.setAction(Constants.ACTION_ACCEPT);
                acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, invite);
                acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
                getApplicationContext().startActivity(acceptIntent);
            }

            @Override
            public void onReject() {
                super.onReject();
                connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
                connection.destroy();
                connection = null;
                // extract call invite
                Bundle inviteBundle = request.getExtras().getBundle(Constants.INCOMING_CALL_INVITE);
                inviteBundle.setClassLoader(CallInvite.class.getClassLoader());
                CallInvite invite = inviteBundle.getParcelable(Constants.INCOMING_CALL_INVITE);
                // notify activity
                Intent rejectIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
                rejectIntent.setAction(Constants.ACTION_REJECT);
                rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, invite);
                rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
                startService(rejectIntent);
            }

            @Override
            public void onPostDialContinue(boolean proceed) {
                super.onPostDialContinue(true);
            }
        };
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        Bundle requestExtras = request.getExtras();
        final Uri callee = requestExtras.getParcelable(Constants.INCOMING_CALL_ADDRESS);
        final Uri caller = requestExtras.getParcelable(Constants.OUTGOING_CALL_ADDRESS);
        if (null != callee) {
            connection.setAddress(callee, TelecomManager.PRESENTATION_ALLOWED);
        } else if (null != caller) {
            connection.setAddress(caller, TelecomManager.PRESENTATION_ALLOWED);
        } else {
            connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        connection.setExtras(requestExtras);
        return connection;
    }

    /*
     * Send call request to the VoiceConnectionServiceActivity
     */
    private void sendCallRequestToActivity(String action) {
        Intent intent = new Intent(action);
        Bundle extras = new Bundle();
        switch (action) {
            case ACTION_OUTGOING_CALL:
                Uri address = connection.getAddress();
                extras.putParcelable(Constants.OUTGOING_CALL_ADDRESS, address);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtras(extras);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DISCONNECT_CALL:
                extras.putInt("Reason", DisconnectCause.LOCAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtras(extras);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DTMF_SEND:
                String d = connection.getExtras().getString(DTMF);
                extras.putString(DTMF, connection.getExtras().getString(DTMF));
                intent.putExtras(extras);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            default:
                break;
        }
    }

}
