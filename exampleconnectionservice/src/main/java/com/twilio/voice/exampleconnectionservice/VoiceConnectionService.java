package com.twilio.voice.exampleconnectionservice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.os.Handler;
import android.widget.Toast;

import static com.twilio.voice.exampleconnectionservice.VoiceActivity.ACTION_DISCONNECT_CALL;
import static com.twilio.voice.exampleconnectionservice.VoiceActivity.ACTION_DTMF_SEND;
import static com.twilio.voice.exampleconnectionservice.VoiceActivity.ACTION_OUTGOING_CALL;
import static com.twilio.voice.exampleconnectionservice.VoiceActivity.CALLEE;
import static com.twilio.voice.exampleconnectionservice.VoiceActivity.DTMF;
import static com.twilio.voice.exampleconnectionservice.VoiceActivity.OUTGOING_CALL_ADDRESS;

public class VoiceConnectionService extends ConnectionService {

    private static Connection connection;

    public static Connection getConnection() {
        return connection;
    }

    public static void deinitConnection() {
        connection = null;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {

        Toast.makeText(getApplicationContext(), "onCreateIncomingConnection called", Toast.LENGTH_SHORT).show();
        Connection incomingCallCannection = createConnection(request);
        incomingCallCannection.setRinging();
        return incomingCallCannection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Toast.makeText(getApplicationContext(), "onCreateOutgoingConnection called", Toast.LENGTH_SHORT).show();

        Connection outgoingCallConnection = createConnection(request);
        outgoingCallConnection.setDialing();

        return outgoingCallConnection;
    }

    private Connection createConnection(ConnectionRequest request) {
        connection = new Connection() {

            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            sendCallRequestToActivity(ACTION_OUTGOING_CALL);
                        }
                    });
                }
            }

            @Override
            public void onCallAudioStateChanged(CallAudioState state) {
                Toast.makeText(getApplicationContext(), "onCallAudioStateChanged called", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPlayDtmfTone(char c) {
                Toast.makeText(getApplicationContext(), "onPlayDtmfTone called", Toast.LENGTH_SHORT).show();
                Bundle extras = new Bundle();
                extras.putString(DTMF, Character.toString(c));
                connection.setExtras(extras);
                final Handler handler = new Handler();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        sendCallRequestToActivity(ACTION_DTMF_SEND);
                    }
                });
            }

            @Override
            public void onDisconnect() {
                super.onDisconnect();
                connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                connection.destroy();
                connection = null;
                final Handler handler = new Handler();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        sendCallRequestToActivity(ACTION_DISCONNECT_CALL);
                    }
                });
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
            }

            @Override
            public void onAnswer() {
                super.onAnswer();
            }

            @Override
            public void onReject() {
                super.onReject();
                connection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                connection.destroy();
            }

            @Override
            public void onPostDialContinue(boolean proceed) {
                super.onPostDialContinue(true);
            }
        };
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        if (request.getExtras().getString(CALLEE) == null) {
            connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        } else {
            connection.setAddress(Uri.parse(request.getExtras().getString(CALLEE)), TelecomManager.PRESENTATION_ALLOWED);
        }
        connection.setDialing();
        connection.setExtras(request.getExtras());
        return connection;
    }

    /*
     * Send the CallInvite to the VoiceActivity
     */
    private void sendCallRequestToActivity(String action) {
        Intent intent = new Intent(action);
        Bundle extras = new Bundle();
        switch (action) {
            case ACTION_OUTGOING_CALL:
                Uri address = connection.getAddress();
                extras.putString(OUTGOING_CALL_ADDRESS, address.toString());
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
