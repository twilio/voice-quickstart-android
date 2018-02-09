package com.twilio.voice.quickstart.fcm;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallInvite;
import com.twilio.voice.MessageException;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;
import com.twilio.voice.quickstart.R;
import com.twilio.voice.quickstart.SoundPoolManager;
import com.twilio.voice.quickstart.VoiceActivity;

import java.util.Map;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "VoiceFCMService";
    private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";
    private static final String VOICE_CHANNEL = "default";

    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Received onMessageReceived()");
        Log.d(TAG, "Bundle data: " + remoteMessage.getData());
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            final int notificationId = (int) System.currentTimeMillis();
            Voice.handleMessage(this, data, new MessageListener() {
                @Override
                public void onCallInvite(CallInvite callInvite) {
                    VoiceFirebaseMessagingService.this.notify(callInvite, notificationId);
                    VoiceFirebaseMessagingService.this.sendCallInviteToActivity(callInvite, notificationId);
                }

                @Override
                public void onError(MessageException messageException) {
                    Log.e(TAG, messageException.getLocalizedMessage());
                }
            });
        }
    }

    private void notify(CallInvite callInvite, int notificationId) {
        String callSid = callInvite.getCallSid();
        Notification notification = null;

        if (callInvite.getState() == CallInvite.State.PENDING) {
            Intent intent = new Intent(this, VoiceActivity.class);
            intent.setAction(VoiceActivity.ACTION_INCOMING_CALL);
            intent.putExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            intent.putExtra(VoiceActivity.INCOMING_CALL_INVITE, callInvite);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_ONE_SHOT);
            /*
             * Pass the notification id and call sid to use as an identifier to cancel the
             * notification later
             */
            Bundle extras = new Bundle();
            extras.putInt(NOTIFICATION_ID_KEY, notificationId);
            extras.putString(CALL_SID_KEY, callSid);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel callInviteChannel = new NotificationChannel(VOICE_CHANNEL,
                        "Primary Voice Channel", NotificationManager.IMPORTANCE_DEFAULT);
                callInviteChannel.setLightColor(Color.GREEN);
                callInviteChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                notificationManager.createNotificationChannel(callInviteChannel);

                notification = buildNotification(callInvite.getFrom() + " is calling.", pendingIntent, extras);
                notificationManager.notify(notificationId, notification);
            } else {
                NotificationCompat.Builder notificationBuilder =
                        new NotificationCompat.Builder(this)
                                .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                                .setContentTitle(getString(R.string.app_name))
                                .setContentText(callInvite.getFrom() + " is calling.")
                                .setAutoCancel(true)
                                .setExtras(extras)
                                .setContentIntent(pendingIntent)
                                .setGroup("test_app_notification")
                                .setColor(Color.rgb(214, 10, 37));

                notificationManager.notify(notificationId, notificationBuilder.build());
            }
        } else {
            SoundPoolManager.getInstance(this).stopRinging();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                /*
                 * If the incoming call was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    notification = statusBarNotification.getNotification();
                    Bundle extras = notification.extras;
                    String notificationCallSid = extras.getString(CALL_SID_KEY);

                    if (callSid.equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(NOTIFICATION_ID_KEY));
                    } else {
                        sendCallInviteToActivity(callInvite, notificationId);
                    }
                }
            } else {
                /*
                 * Prior to Android M the notification manager did not provide a list of
                 * active notifications so we lazily clear all the notifications when
                 * receiving a cancelled call.
                 *
                 * In order to properly cancel a notification using
                 * NotificationManager.cancel(notificationId) we should store the call sid &
                 * notification id of any incoming calls using shared preferences or some other form
                 * of persistent storage.
                 */
                notificationManager.cancelAll();
            }
        }
    }

    /*
     * Send the CallInvite to the VoiceActivity. Start the activity if it is not running already.
     */
    private void sendCallInviteToActivity(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(this, VoiceActivity.class);
        intent.setAction(VoiceActivity.ACTION_INCOMING_CALL);
        intent.putExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(VoiceActivity.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.startActivity(intent);
    }

    /**
     * Build a notification.
     *
     * @param text the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    public Notification buildNotification(String text, PendingIntent pendingIntent, Bundle extras) {
        return new Notification.Builder(getApplicationContext(), VOICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setExtras(extras)
                .setAutoCancel(true)
                .build();
    }
}
