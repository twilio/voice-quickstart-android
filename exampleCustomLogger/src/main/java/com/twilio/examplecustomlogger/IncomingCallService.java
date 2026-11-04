package com.twilio.examplecustomlogger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

public class IncomingCallService extends FirebaseMessagingService implements MessageListener {
    static final String ACTION_CANCELLED = "com.twilio.examplecustomlogger.CALL_CANCELLED";
    static final String EXTRA_CALL_INVITE = "EXTRA_CALL_INVITE";
    static final String EXTRA_ACCEPT = "EXTRA_ACCEPT";

    private static final String TAG = "IncomingCallService";
    private static final String CHANNEL_ID = "incoming_calls";
    static final int NOTIFICATION_ID = 1;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "Received FCM message from " + remoteMessage.getFrom());
        if (!remoteMessage.getData().isEmpty()
                && !Voice.handleMessage(this, remoteMessage.getData(), this)) {
            Log.e(TAG, "Not a valid Twilio Voice payload: " + remoteMessage.getData());
        }
    }

    @Nullable
    static CallInvite getInvite(Intent intent) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(EXTRA_CALL_INVITE, CallInvite.class)
                : intent.getParcelableExtra(EXTRA_CALL_INVITE);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "onNewToken");
    }

    @Override
    public void onCallInvite(@NonNull CallInvite callInvite) {
        Log.d(TAG, "onCallInvite " + callInvite.getCallSid());
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH));
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent openIntent = new Intent(this, CustomLoggerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(EXTRA_CALL_INVITE, callInvite);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent, flags);

        Intent acceptIntent = new Intent(openIntent).putExtra(EXTRA_ACCEPT, true);
        PendingIntent acceptPending = PendingIntent.getActivity(this, 1, acceptIntent, flags);

        Intent declineIntent =
                new Intent(this, CallActionReceiver.class)
                        .setAction(CallActionReceiver.ACTION_DECLINE)
                        .putExtra(EXTRA_CALL_INVITE, callInvite);
        PendingIntent declinePending = PendingIntent.getBroadcast(this, 2, declineIntent, flags);

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.sym_call_incoming)
                        .setContentTitle("Incoming call")
                        .setContentText(callInvite.getFrom())
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(openPending)
                        .setFullScreenIntent(openPending, true)
                        .addAction(0, getString(R.string.decline), declinePending)
                        .addAction(0, getString(R.string.accept), acceptPending)
                        .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onCancelledCallInvite(
            @NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException error) {
        Log.d(TAG, "onCancelledCallInvite " + cancelledCallInvite.getCallSid());
        getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        sendBroadcast(new Intent(ACTION_CANCELLED).setPackage(getPackageName()));
    }
}
