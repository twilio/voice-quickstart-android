package com.twilio.voice.exampleconnectionservice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
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

public class VoiceConnectionService extends ConnectionService {

    private static String TAG = "VoiceConnectionService";
    private static Connection mConnection;

    public static Connection getConnection() {
        return mConnection;
    }

    public static void deinitConnection() {
        mConnection = null;
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
        mConnection = new Connection() {
            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendCallRequestToActivity(ACTION_OUTGOING_CALL);
                        }
                    }, 100); // TODO decide on the delay
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
                extras.putString("DTMF", Character.toString(c));
                mConnection.setExtras(extras);
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendCallRequestToActivity(ACTION_DTMF_SEND);
                    }
                }, 100); // TODO decide on the delay
            }

            @Override
            public void onDisconnect() {
                super.onDisconnect();
                mConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                mConnection.destroy();
                mConnection = null;
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendCallRequestToActivity(ACTION_DISCONNECT_CALL);
                    }
                }, 100); // TODO decide on the delay
            }

            @Override
            public void onSeparate() {
                super.onSeparate();
            }

            @Override
            public void onAbort() {
                super.onAbort();
                mConnection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                mConnection.destroy();
            }

            @Override
            public void onHold() {
                super.onHold();
            }

            @Override
            public void onAnswer() {
                super.onAnswer();
                mConnection.setActive();
            }

            @Override
            public void onReject() {
                super.onReject();
                mConnection.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                mConnection.destroy();

            }

            @Override
            public void onPostDialContinue(boolean proceed) {
                super.onPostDialContinue(true);
            }
        };
        mConnection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        if (request.getExtras().getString("to") == null) {
            mConnection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        } else {
            mConnection.setAddress(Uri.parse(request.getExtras().getString("to")), TelecomManager.PRESENTATION_ALLOWED);
        }

        mConnection.setExtras(request.getExtras());
        return mConnection;
    }

    /*
     * Send the CallInvite to the VoiceActivity
     */
    private void sendCallRequestToActivity(String action) {
        Intent intent = new Intent(action);
        Bundle extras = new Bundle();
        switch (action) {
            case ACTION_OUTGOING_CALL:
                Uri address = mConnection.getAddress();
                extras.putString(VoiceActivity.OUTGOING_CALL_ADDRESS, address.toString());
                intent.putExtras(extras);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DISCONNECT_CALL:
                extras.putInt("Reason", DisconnectCause.LOCAL);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            case ACTION_DTMF_SEND:
                extras.putString("DTMF", mConnection.getExtras().getString("DTMF"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                //LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
            default:
                break;
        }
    }

}
