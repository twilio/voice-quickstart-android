package com.twilio.voice.quickstart;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.twilio.voice.CallInvite;
import com.twilio.voice.quickstart.VoiceActivity;
import com.twilio.voice.quickstart.fcm.VoiceFirebaseMessagingService;

public class IncomingCallNotificationService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        CallInvite callInvite = intent.getParcelableExtra(VoiceActivity.INCOMING_CALL_INVITE);
        int notificationId = intent.getIntExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, 0);
        if (VoiceFirebaseMessagingService.ACTION_ACCEPT.equals(action)) {
            Intent activeCallIntent = new Intent(this, VoiceActivity.class);
            activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activeCallIntent.putExtra(VoiceActivity.INCOMING_CALL_INVITE, callInvite);
            activeCallIntent.putExtra(VoiceActivity.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            activeCallIntent.setAction(action);
            startActivity(activeCallIntent);
        } else if (VoiceFirebaseMessagingService.ACTION_REJECT.equals(action)) {
            callInvite.reject(getApplicationContext());
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}