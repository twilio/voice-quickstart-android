package com.twilio.voice.quickstart;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.CallInvite;

public class IncomingCallNotificationService extends Service {
    private static final String TAG = IncomingCallNotificationService.class.getSimpleName();

    private PhoneAccountHandle phoneAccountHandle;
    private TelecomManager telecomManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // register telecom account info
        Context appContext = this.getApplicationContext();
        String appName = this.getString(R.string.connection_service_name);
        phoneAccountHandle =
                new PhoneAccountHandle(new ComponentName(appContext, VoiceConnectionService.class),
                        appName);
        telecomManager = (TelecomManager) appContext.getSystemService(TELECOM_SERVICE);
        PhoneAccount phoneAccount = new PhoneAccount.Builder(phoneAccountHandle, appName)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build();
        telecomManager.registerPhoneAccount(phoneAccount);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (action != null) {
            CallInvite callInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            int notificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
            switch (action) {
                case Constants.ACTION_INCOMING_CALL:
                    handleIncomingCall(callInvite, notificationId);
                    break;
                case Constants.ACTION_OUTGOING_CALL:
                    handleOutgoingCall(intent);
                    break;
                case Constants.ACTION_ACCEPT:
                    accept(callInvite, notificationId);
                    break;
                case Constants.ACTION_REJECT:
                    reject(callInvite);
                    break;
                case Constants.ACTION_CANCEL_CALL:
                    handleCancelledCall(intent);
                    break;
                default:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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

        return buildNotification(callInvite.getFrom() + " is calling.",
                pendingIntent,
                extras,
                callInvite,
                notificationId,
                createChannel(channelImportance));
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    private Notification buildNotification(String text, PendingIntent pendingIntent, Bundle extras,
                                           final CallInvite callInvite,
                                           int notificationId,
                                           String channelId) {
        Intent rejectIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        rejectIntent.setAction(Constants.ACTION_REJECT);
        rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piRejectIntent = PendingIntent.getService(getApplicationContext(), notificationId, rejectIntent, PendingIntent.FLAG_IMMUTABLE);

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
        // notify telephony service of call approval
        VoiceConnectionService.getConnection().setActive();
    }

    private void reject(CallInvite callInvite) {
        endForeground();
        callInvite.reject(getApplicationContext());
        // notify telephony service of call rejection
        Connection cxn = VoiceConnectionService.getConnection();
        if (null != cxn) {
            cxn.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            VoiceConnectionService.releaseConnection();
        }
    }

    private void handleCancelledCall(Intent intent) {
        endForeground();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void handleIncomingCall(CallInvite callInvite, int notificationId) {
        setCallInProgressNotification(callInvite, notificationId);
        // register new call with telecom subsystem
        Bundle inviteBundle = new Bundle(CallInvite.class.getClassLoader());
        inviteBundle.putParcelable(Constants.INCOMING_CALL_INVITE, callInvite);
        Bundle callInfo = new Bundle();
        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, callInvite.getFrom(), null);
        callInfo.putBundle(Constants.INCOMING_CALL_INVITE, inviteBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        callInfo.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
        telecomManager.addNewIncomingCall(phoneAccountHandle, callInfo);
    }

    private void handleOutgoingCall(Intent intent) {
        // place a call with the telecom subsystem
        final Bundle extra = intent.getExtras();
        if (null != extra) {
            Bundle callInfo = new Bundle();
            final Uri recipient = extra.getParcelable(Constants.OUTGOING_CALL_RECIPIENT);
            final int permissionsState =
                    ActivityCompat.checkSelfPermission(this,
                                                        Manifest.permission.MANAGE_OWN_CALLS);
            callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extra);
            callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
            callInfo.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
            if (permissionsState == PackageManager.PERMISSION_GRANTED) {
                telecomManager.placeCall(recipient, callInfo);
            }
        }
    }


    private void endForeground() {
        stopForeground(true);
    }

    private void setCallInProgressNotification(CallInvite callInvite, int notificationId) {
        if (isAppVisible()) {
            Log.i(TAG, "setCallInProgressNotification - app is visible.");
            startForegroundService(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_LOW));
        } else {
            Log.i(TAG, "setCallInProgressNotification - app is NOT visible.");
            startForegroundService(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_HIGH));
        }
    }

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
