package com.twilio.voice.quickstart;

import static com.twilio.voice.quickstart.Constants.ACTION_CANCEL_CALL;
import static com.twilio.voice.quickstart.Constants.ACTION_FCM_TOKEN;
import static com.twilio.voice.quickstart.Constants.ACTION_INCOMING_CALL;
import static com.twilio.voice.quickstart.Constants.ACTION_REJECT_CALL;
import static com.twilio.voice.quickstart.Constants.FCM_TOKEN;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.Call;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.Voice;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Vector;

public class VoiceService extends Service {
    private static final Logger log = new Logger(VoiceService.class);
    private String fcmToken;
    private SoundPoolManager soundPoolManager;
    private Map<String, CallRecord> callDatabase;
    private WeakReference<VoiceActivity> voiceActiviy;

    public static class CallRecord {
        public CallInvite callInvite;
        public CallRecord(final CallInvite callInvite) {
            this.callInvite = callInvite;
        }
    }

    public VoiceService() {
        fcmToken = "unavailable";
        soundPoolManager = new SoundPoolManager(this);
        callDatabase = new HashMap<>();
        voiceActiviy = new WeakReference<>(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder() {
            VoiceService getService() {
                return VoiceService.this;
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case ACTION_FCM_TOKEN:
                fcmToken = Objects.requireNonNull(intent.getStringExtra(FCM_TOKEN));
                break;
            case ACTION_INCOMING_CALL:
                incomingCall(
                        Objects.requireNonNull(
                                intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE)));
                break;
            case ACTION_CANCEL_CALL:
                cancelledCall(
                        Objects.requireNonNull(
                                intent.getParcelableExtra(Constants.CANCELLED_CALL_INVITE)));
                break;
            case ACTION_REJECT_CALL:
                rejectIncomingCall(
                        Objects.requireNonNull(intent.getStringExtra(Constants.CALL_SID)));
                break;
            default:
                log.error("should never get here");
        }
        return START_NOT_STICKY;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void registerVoiceActivity(final VoiceActivity voiceActivity) {
        voiceActiviy = new WeakReference<>(voiceActivity);
    }

    public void rejectIncomingCall(final String callSID) {
        // find call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callSID));

        // remove notification
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);

        // kill ringer
        soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);

        // remove call record
        callDatabase.remove(callRecord);

        // notify voice activity
        if (null != voiceActiviy.get()) {
            // todo
        }
    }

    private void incomingCall(@NonNull final CallInvite callInvite) {
        // create call record
        callDatabase.put(callInvite.getCallSid(), new CallRecord(callInvite));

        // create incoming call notification
        // todo

        // create ringer sound
        soundPoolManager.playSound(SoundPoolManager.Sound.RINGER);

        // notify voice activity
        if (null != voiceActiviy.get()) {
            // todo
        }
    }

    private void cancelledCall(@NonNull final CancelledCallInvite cancelledCallInvite) {
        // find call record
        final CallRecord callRecord =
                Objects.requireNonNull(callDatabase.get(cancelledCallInvite.getCallSid()));

        // remove notification
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);

        // kill ringer
        soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);

        // remove call record
        callDatabase.remove(callRecord);

        // notify voice activity
        if (null != voiceActiviy.get()) {
            // todo
        }
    }


    //// old


    private Notification createNotification(CallInvite callInvite, int notificationId, int channelImportance) {
        Intent intent = new Intent(this, NotificationProxyActivity.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL_NOTIFICATION);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_IMMUTABLE);
        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putString(Constants.CALL_SID_KEY, callInvite.getCallSid());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return buildNotification(callInvite.getFrom() + " is calling.",
                    pendingIntent,
                    extras,
                    callInvite,
                    notificationId,
                    createChannel(channelImportance));
        } else {
            //noinspection deprecation
            return new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(callInvite.getFrom() + " is calling.")
                    .setAutoCancel(true)
                    .setExtras(extras)
                    .setContentIntent(pendingIntent)
                    .setGroup("test_app_notification")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setColor(Color.rgb(214, 10, 37)).build();
        }
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    private Notification buildNotification(String text, PendingIntent pendingIntent, Bundle extras,
                                          final CallInvite callInvite,
                                          int notificationId,
                                          String channelId) {
        Intent rejectIntent = new Intent(getApplicationContext(), NotificationProxyActivity.class);
        rejectIntent.setAction(Constants.ACTION_REJECT);
        rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piRejectIntent = PendingIntent.getActivity(getApplicationContext(), notificationId, rejectIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent acceptIntent = new Intent(getApplicationContext(), NotificationProxyActivity.class);
        acceptIntent.setAction(Constants.ACTION_ACCEPT);
        acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piAcceptIntent = PendingIntent.getActivity(getApplicationContext(), notificationId, acceptIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder =
                new Notification.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setExtras(extras)
                        .setAutoCancel(true)
                        .addAction(android.R.drawable.ic_menu_delete, getString(R.string.decline), piRejectIntent)
                        .addAction(android.R.drawable.ic_menu_call, getString(R.string.answer), piAcceptIntent)
                        .setFullScreenIntent(pendingIntent, true);

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createChannel(int channelImportance) {
        NotificationChannel callInviteChannel = new NotificationChannel(Constants.VOICE_CHANNEL_HIGH_IMPORTANCE,
                "Primary Voice Channel", NotificationManager.IMPORTANCE_HIGH);
        String channelId = Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;

        if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
            callInviteChannel = new NotificationChannel(Constants.VOICE_CHANNEL_LOW_IMPORTANCE,
                    "Primary Voice Channel", NotificationManager.IMPORTANCE_LOW);
            channelId = Constants.VOICE_CHANNEL_LOW_IMPORTANCE;
        }
        callInviteChannel.setLightColor(Color.GREEN);
        callInviteChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(callInviteChannel);

        return channelId;
    }

    private void accept(CallInvite callInvite, int notificationId) {
        endForeground();
    }

    private void reject(CallInvite callInvite) {
        endForeground();
        callInvite.reject(getApplicationContext());
    }

    private void handleCancelledCall(Intent intent) {
        endForeground();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void handleIncomingCall(Intent intent, CallInvite callInvite, int notificationId) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setCallInProgressNotification(callInvite, notificationId);
        }
    }

    private void endForeground() {
        stopForeground(true);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void setCallInProgressNotification(CallInvite callInvite, int notificationId) {
        if (isAppVisible()) {
            Log.i(TAG, "setCallInProgressNotification - app is visible.");
            startForegroundService(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_LOW));
        } else {
            Log.i(TAG, "setCallInProgressNotification - app is NOT visible.");
            startForeground(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void startForegroundService(final int id, final Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(id, notification);
        }
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }
}
