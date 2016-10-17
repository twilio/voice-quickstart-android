package com.twilio.voice.quickstart.gcm;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.twilio.voice.IncomingCallMessage;
import com.twilio.voice.quickstart.R;
import com.twilio.voice.quickstart.VoiceActivity;

public class VoiceGCMListenerService extends GcmListenerService {

    private static final String TAG = "VoiceGCMListenerService";

    /*
     * Notification related keys
     */
    private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";

    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onMessageReceived(String from, Bundle bundle) {
        Log.d(TAG, "onMessageReceived " + from);

        if (IncomingCallMessage.isValidMessage(bundle)) {
            /*
             * Generate a unique notification id using the system time
             */
            int notificationId = (int)System.currentTimeMillis();

            /*
             * Create an IncomingCallMessage from the bundle
             */
            IncomingCallMessage incomingCallMessage = new IncomingCallMessage(bundle);

            showNotification(incomingCallMessage, notificationId);
            sendIncomingCallMessageToActivity(incomingCallMessage, notificationId);
        }

    }

    /*
     * Show the notification in the Android notification drawer
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(IncomingCallMessage incomingCallMessage, int notificationId) {
        String callSid = incomingCallMessage.getCallSid();

        if (!incomingCallMessage.isCancelled()) {
            /*
             * Create a PendingIntent to specify the action when the notification is
             * selected in the notification drawer
             */
            Intent intent = new Intent(this, VoiceActivity.class);
            intent.setAction(VoiceActivity.ACTION_INCOMING_CALL);
            intent.putExtra(VoiceActivity.INCOMING_CALL_MESSAGE, incomingCallMessage);
            intent.putExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            /*
             * Pass the notification id and call sid to use as an identifier to cancel the
             * notification later
             */
            Bundle extras = new Bundle();
            extras.putInt(NOTIFICATION_ID_KEY, notificationId);
            extras.putString(CALL_SID_KEY, callSid);

            /*
             * Create the notification shown in the notification drawer
             */
            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(R.drawable.ic_call_white_24px)
                            .setContentTitle(getString(R.string.app_name))
                            .setContentText(incomingCallMessage.getFrom() + " is calling.")
                            .setAutoCancel(true)
                            .setExtras(extras)
                            .setContentIntent(pendingIntent)
                            .setColor(Color.rgb(214, 10, 37));

            notificationManager.notify(notificationId, notificationBuilder.build());
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                /*
                 * If the incoming call was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    Bundle extras = notification.extras;
                    String notificationCallSid = extras.getString(CALL_SID_KEY);
                    if (callSid.equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(NOTIFICATION_ID_KEY));
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
     * Send the IncomingCallMessage to the VoiceActivity
     */
    private void sendIncomingCallMessageToActivity(IncomingCallMessage incomingCallMessage, int notificationId) {
        Intent intent = new Intent(VoiceActivity.ACTION_INCOMING_CALL);
        intent.putExtra(VoiceActivity.INCOMING_CALL_MESSAGE, incomingCallMessage);
        intent.putExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
