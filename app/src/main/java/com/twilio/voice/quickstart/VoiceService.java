package com.twilio.voice.quickstart;

import static com.twilio.voice.quickstart.Constants.ACCESS_TOKEN;
import static com.twilio.voice.quickstart.Constants.ACTION_ACCEPT_CALL;
import static com.twilio.voice.quickstart.Constants.ACTION_CANCEL_CALL;
import static com.twilio.voice.quickstart.Constants.ACTION_INCOMING_CALL;
import static com.twilio.voice.quickstart.Constants.ACTION_REJECT_CALL;
import static com.twilio.voice.quickstart.Constants.CUSTOM_RINGBACK;

import static java.lang.String.format;

import android.app.Notification;
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
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class VoiceService extends Service {
    private static final Logger log = new Logger(VoiceService.class);
    private final NotificationChannelCompat[] notificationChannels;
    private final Map<UUID, CallRecord> callDatabase;
    private final List<Observer> observerList;
    private SoundPoolManager soundPoolManager;
    private String accessToken;
    private boolean playCustomRingback;

    private enum NotificationPriority {
        LOW,
        HIGH
    }
    public static Call activeCall;

    private static class CallRecord {
        public final CallInvite callInvite;
        public int callInviteNotificationId;
        public Call activeCall;
        public int ringCount;
        public long startTime;

        public CallRecord(final Call activeCall) {
            this.callInvite = null;
            this.activeCall = activeCall;
            this.callInviteNotificationId = 0;
            this.ringCount = 0;
        }

        public CallRecord(final CallInvite callInvite) {
            this.callInvite = callInvite;
            this.activeCall = null;
            this.callInviteNotificationId = 0;
        }
    }

    public interface Observer {
        void connectCall(@NonNull final UUID callId, @NonNull final ConnectOptions options);
        void disconnectCall(@NonNull final UUID callId);
        void acceptIncomingCall(@NonNull final UUID callId);
        void rejectIncomingCall(@NonNull final UUID callId);
        void incomingCall(@NonNull final UUID callId, @NonNull final CallInvite callInvite);
        void cancelledCall(@NonNull final UUID callId);
        void muteCall(@NonNull final UUID callId, boolean isMuted);
        void holdCall(@NonNull final UUID callId, boolean isOnHold);
        void registrationSuccessful(@NonNull final String fcmToken);
        void registrationFailed(@NonNull final RegistrationException registrationException);
        void onRinging(@NonNull final UUID callId);
        void onConnectFailure(@NonNull final UUID callId, @NonNull CallException callException);
        void onConnected(@NonNull final UUID callId);
        void onReconnecting(@NonNull final UUID callId, @NonNull CallException callException);
        void onReconnected(@NonNull final UUID callId);
        void onDisconnected(@NonNull final UUID callId, @Nullable CallException callException);
        void onCallQualityWarningsChanged(@NonNull final UUID callId,
                                          @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                          @NonNull Set<Call.CallQualityWarning> previousWarnings);
    }

    public static class Status {
        public static class CallRecord {
            final boolean isMuted;
            final boolean onHold;
            final long timestamp;

            protected CallRecord(final boolean isMuted, final boolean onHold, final long timestamp) {
                this.isMuted = isMuted;
                this.onHold = onHold;
                this.timestamp = timestamp;
            }
        }
        final Map<UUID, CallInvite> pendingCalls;
        final Map<UUID, CallRecord> callMap;
        final UUID activeCall;

        protected Status(final Map<UUID, CallInvite> pendingCalls,
                         final Map<UUID, CallRecord> callList,
                         final UUID activeCall) {
            this.pendingCalls = pendingCalls;
            this.callMap = callList;
            this.activeCall = activeCall;
        }
    }

    public static void sendToVoiceService(final Context context,
                                           final String action,
                                           final UUID callId) {
        final Intent intent = new Intent(context, VoiceService.class);
        intent.setAction(action);
        intent.putExtra(Constants.CALL_UUID, callId);
        context.startService(intent);
    }

    public class VideoServiceBinder extends Binder {
        VideoServiceBinder(Intent intent) {
            accessToken =  intent.getStringExtra(ACCESS_TOKEN);
            playCustomRingback = intent.getBooleanExtra(CUSTOM_RINGBACK, false);
        }
        VoiceService getService() {
            return VoiceService.this;
        }
    }

    public VoiceService() {
        notificationChannels = new NotificationChannelCompat[NotificationPriority.values().length];
        callDatabase = new HashMap<>();
        observerList = new ArrayList<>();
    }

    @Override
    public void onCreate() {
        log.debug("onCreate");
        super.onCreate();
        soundPoolManager = new SoundPoolManager(getApplicationContext());

        // create notification channels
        notificationChannels[NotificationPriority.LOW.ordinal()] =
                new NotificationChannelCompat.Builder(
                        Constants.VOICE_CHANNEL_LOW_IMPORTANCE,
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName("Primary Voice Channel")
                        .setLightColor(Color.GREEN)
                        .build();
        notificationChannels[NotificationPriority.HIGH.ordinal()] =
                new NotificationChannelCompat.Builder(
                        Constants.VOICE_CHANNEL_HIGH_IMPORTANCE,
                        NotificationManagerCompat.IMPORTANCE_HIGH)
                        .setName("Primary Voice Channel")
                        .setLightColor(Color.GREEN)
                        .build();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        for (NotificationChannelCompat notificationChannel : notificationChannels) {
            notificationManager.createNotificationChannel(notificationChannel);
        }

        // try to register VoiceConnectionService as an observer when using connection_service flavor
        try {
            Class<?> clazz = Class.forName("com.twilio.voice.quickstart.VoiceConnectionService");
            Method method = clazz.getMethod("getObserver", Context.class);
            registerObserver((Observer) Objects.requireNonNull(method.invoke(null, this)));
            log.debug("registered VoiceConnectionService");
        } catch (Exception e) {
            log.debug("VoiceConnectionService not available");
        }
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        // cleanup sounds
        soundPoolManager = null;

        // remove notification channels
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        for (NotificationChannelCompat notificationChannel : notificationChannels) {
            notificationManager.deleteNotificationChannel(notificationChannel.getId());
        }
        notificationChannels[NotificationPriority.LOW.ordinal()] = null;
        notificationChannels[NotificationPriority.HIGH.ordinal()] = null;

        // call super
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new VideoServiceBinder(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
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
                            (UUID)Objects.requireNonNull(intent.getSerializableExtra(
                                    Constants.CALL_UUID)));
                    break;
                case ACTION_ACCEPT_CALL:
                    acceptCall(
                            (UUID)Objects.requireNonNull(intent.getSerializableExtra(
                                    Constants.CALL_UUID)));
                    break;
                default:
                    log.error("should never get here");
            }
        }
        return START_NOT_STICKY;
    }

    public Status getStatus() {
        Map<UUID, CallInvite> pendingCalls = new HashMap<>();
        Map<UUID, Status.CallRecord> callMap = new HashMap<>();
        UUID activeCall = null;
        for (Map.Entry<UUID, CallRecord> callEntry: callDatabase.entrySet()) {
            if (null == callEntry.getValue().activeCall &&
                null != callEntry.getValue().callInvite) {
                pendingCalls.put(callEntry.getKey(), callEntry.getValue().callInvite);
            } else {
                callMap.put(callEntry.getKey(), new Status.CallRecord(
                        callEntry.getValue().activeCall.isMuted(),
                        callEntry.getValue().activeCall.isOnHold(),
                        callEntry.getValue().startTime));
                if (null == activeCall) {
                    activeCall = callEntry.getKey();
                } else if (!callEntry.getValue().activeCall.isOnHold()) {
                    activeCall = callEntry.getKey();
                }
            }
        }
        return new Status(pendingCalls, callMap, activeCall);
    }

    public void registerObserver(@NonNull final Observer observer) {
        if (!observerList.contains(observer)) {
            observerList.add(observer);
        }
    }

    public void unregisterObserver(@NonNull final Observer observer) {
        observerList.remove(observer);
    }

    public void registerFCMToken(@NonNull final String fcmToken) {
        Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
    }

    public UUID connectCall(@NonNull final ConnectOptions options) {
        final UUID callId = UUID.randomUUID();

        // connect call & create call record
        CallRecord callRecord = new CallRecord(Voice.connect(this, options, callListener));
        callDatabase.put(callId, callRecord);

        // set call start time
        callRecord.startTime = SystemClock.elapsedRealtime();

        // create & post notification for call
        final Notification notification = createCallNotification(callRecord, NotificationPriority.LOW);
        foregroundService(callRecord.callInviteNotificationId, notification);

        // invoke observers
        for (Observer observer: observerList) {
            observer.connectCall(callId, options);
        }

        // return call Id
        return callId;
    }

    public UUID acceptCall(@NonNull final UUID callId) {
        // find call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callId));

        // remove incoming call notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(callRecord.callInviteNotificationId);

        // create & post notification for call
        final Notification notification = createCallNotification(callRecord, NotificationPriority.LOW);
        foregroundService(callRecord.callInviteNotificationId, notification);

        // kill ringer
        soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);

        // set call start time
        callRecord.startTime = SystemClock.elapsedRealtime();

        // accept call
        callRecord.activeCall = callRecord.callInvite.accept(this, callListener);

        // invoke observers
        for (Observer observer: observerList) {
            observer.acceptIncomingCall(callId);
        }

        return callId;
    }

    public void disconnectCall(@NonNull final UUID callId) {
        // find call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callId));

        // play disconnect sound
        soundPoolManager.playSound(SoundPoolManager.Sound.DISCONNECT);

        // disconnect call
        callRecord.activeCall.disconnect();

        // invoke observers
        for (Observer observer: observerList) {
            observer.disconnectCall(callId);
        }
    }

    public void muteCall(@NonNull final UUID callId) {
        // find call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callId));

        // mute call
        boolean muteState = callRecord.activeCall.isMuted();
        callRecord.activeCall.mute(!muteState);

        // invoke observers
        for (Observer observer: observerList) {
            observer.muteCall(callId, !muteState);
        }
    }

    public void holdCall(@NonNull final UUID callId) {
        // find call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callId));

        // hold call
        boolean holdState = callRecord.activeCall.isOnHold();
        callRecord.activeCall.hold(!holdState);

        // invoke observers
        for (Observer observer: observerList) {
            observer.holdCall(callId, !holdState);
        }
    }

    public void rejectIncomingCall(final UUID callId) {
        // find & remove call record
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.remove(callId));

        // remove notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(callRecord.callInviteNotificationId);

        // kill ringer
        soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);

        // reject call
        callRecord.callInvite.reject(this);

        // notify observers
        for (Observer observer: observerList) {
            observer.rejectIncomingCall(callId);
        }
    }

    private void incomingCall(@NonNull final CallInvite callInvite) {
        // create call record
        final UUID uuid = UUID.randomUUID();
        final CallRecord callRecord = new CallRecord(callInvite);
        callDatabase.put(uuid, callRecord);

        // create incoming call notification
        NotificationPriority priority =
                isAppVisible() ? NotificationPriority.LOW : NotificationPriority.HIGH;
        final Notification notification = createIncomingCallNotification(uuid, callRecord, priority);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(callRecord.callInviteNotificationId, notification);

        // create ringer sound
        soundPoolManager.playSound(SoundPoolManager.Sound.RINGER);

        // notify observers
        for (Observer observer: observerList) {
            observer.incomingCall(uuid, callInvite);
        }
    }

    private void cancelledCall(@NonNull final CancelledCallInvite cancelledCallInvite) {
        // find call record
        final UUID callId =
                Objects.requireNonNull(findAssociatedCallId(cancelledCallInvite.getCallSid()));
        final CallRecord callRecord = Objects.requireNonNull(callDatabase.remove(callId));

        // remove notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(callRecord.callInviteNotificationId);

        // kill ringer
        soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);

        // notify observers
        for (Observer observer: observerList) {
            observer.cancelledCall(callId);
        }
    }

    private Notification createIncomingCallNotification(
            final UUID callId,
            final CallRecord callRecord,
            final NotificationPriority priority) {
        final int notificationId = generateRandomId();
        String channelId = (priority == NotificationPriority.LOW) ?
                Constants.VOICE_CHANNEL_LOW_IMPORTANCE : Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;

        // pass the call sid to use an identifier to retrieve the call info later
        Bundle extras = new Bundle();
        extras.putSerializable(Constants.CALL_UUID, callId);

        // create pending intents
        Intent foregroundIntent = new Intent(this, VoiceActivity.class);
        foregroundIntent.setAction(Constants.ACTION_INCOMING_CALL_NOTIFICATION);
        foregroundIntent.putExtra(Constants.CALL_UUID, callId);
        foregroundIntent.putExtra(Constants.INCOMING_CALL_INVITE, callRecord.callInvite);
        foregroundIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingForegroundIntent = PendingIntent.getActivity(this,
                notificationId, foregroundIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent rejectIntent = new Intent(getApplicationContext(), VoiceService.class);
        rejectIntent.setAction(Constants.ACTION_REJECT_CALL);
        rejectIntent.putExtra(Constants.CALL_UUID, callId);
        PendingIntent pendingRejectIntent = PendingIntent.getService(
                this, notificationId, rejectIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent acceptIntent = new Intent(getApplicationContext(), VoiceActivity.class);
        acceptIntent.setAction(ACTION_ACCEPT_CALL);
        acceptIntent.putExtra(Constants.CALL_UUID, callId);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingAcceptIntent = PendingIntent.getActivity(
                this, notificationId, acceptIntent, PendingIntent.FLAG_IMMUTABLE);

        callRecord.callInviteNotificationId = notificationId;
        // create notification
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(callRecord.callInvite.getFrom() + " is calling")
                .setCategory(Notification.CATEGORY_CALL)
                .setExtras(extras)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_delete, getString(R.string.decline), pendingRejectIntent)
                .addAction(android.R.drawable.ic_menu_call, getString(R.string.answer), pendingAcceptIntent)
                .setContentIntent(pendingForegroundIntent)
                .setFullScreenIntent(pendingForegroundIntent, true)
                .build();
    }

    private Notification createCallNotification(
            final CallRecord callRecord,
            final NotificationPriority priority) {
        final int notificationId = generateRandomId();
        String channelId = (priority == NotificationPriority.LOW) ?
                Constants.VOICE_CHANNEL_LOW_IMPORTANCE : Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;

        // create pending intents
        Intent foregroundIntent = new Intent(this, VoiceActivity.class);
        PendingIntent pendingForegroundIntent = PendingIntent.getActivity(this,
                notificationId, foregroundIntent, PendingIntent.FLAG_IMMUTABLE);

        callRecord.callInviteNotificationId = notificationId;
        // create notification
        return new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Active Call")
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(false)
                .setContentIntent(pendingForegroundIntent)
                .build();
    }

    private UUID findAssociatedCallId(final String callSid) {
        for (Map.Entry<UUID, CallRecord> entry: callDatabase.entrySet()) {
            if (null != entry.getValue().callInvite &&
                    entry.getValue().callInvite.getCallSid().equals(callSid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private UUID findAssociatedCallId(final Call call) {
        for (Map.Entry<UUID, CallRecord> entry: callDatabase.entrySet()) {
            if (null != entry.getValue().activeCall && entry.getValue().activeCall == call) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    private int generateRandomId() {
        int newId;
        Random generator = new Random(System.currentTimeMillis());
        for (newId = generator.nextInt(); newId == 0; newId = generator.nextInt()) { }
        return newId;
    }

    private void foregroundService(final int notificationId, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                    this,
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(notificationId, notification);
        }
    }

    private final RegistrationListener registrationListener = new RegistrationListener() {
        @Override
        public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
            log.debug("Successfully registered FCM");
            // notify observers
            for (Observer observer: observerList) {
                observer.registrationSuccessful(fcmToken);
            }
        }

        @Override
        public void onError(@NonNull RegistrationException registrationException,
                            @NonNull String accessToken,
                            @NonNull String fcmToken) {
            log.warning("failed to registered FCM");
            // notify observers
            for (Observer observer: observerList) {
                observer.registrationFailed(registrationException);
            }
        }
    };

    private final Call.Listener callListener = new Call.Listener() {
        @Override
        public void onRinging(@NonNull Call call) {
            log.debug("Ringing");

            // find call record & remove
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));
            final CallRecord callRecord = Objects.requireNonNull(callDatabase.get(callId));

            // When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
            // is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
            // the call is ringing and awaiting to be accepted on the callee's side. The application
            // can use the `SoundPoolManager` to play custom audio files between the
            // `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
            if (1 == callRecord.ringCount++ && playCustomRingback) {
                soundPoolManager.playSound(SoundPoolManager.Sound.RINGER);
            }

            // notify observers
            for (Observer observer: observerList) {
                observer.onRinging(callId);
            }
        }

        @Override
        public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
            log.debug("Connect failure: " + logException(callException));

            // find call record & remove
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));
            Objects.requireNonNull(callDatabase.remove(callId));

            // kill ringer
            if (playCustomRingback) {
                soundPoolManager.stopSound(SoundPoolManager.Sound.RINGER);
            }

            // remove in-call notification
            ServiceCompat.stopForeground(
                    VoiceService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);

            // notify observers
            for (Observer observer: observerList) {
                observer.onConnectFailure(callId, callException);
            }
        }

        @Override
        public void onConnected(@NonNull Call call) {
            log.debug("Connected");

            // find call record
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));

            // kill ringer
            if (playCustomRingback) {
                soundPoolManager.playSound(SoundPoolManager.Sound.RINGER);
            }

            activeCall = call;

            // notify observers
            for (Observer observer: observerList) {
                observer.onConnected(callId);
            }
        }

        @Override
        public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
            log.debug("Reconnecting: " + logException(callException));

            // find call record
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));

            // notify observers
            for (Observer observer: observerList) {
                observer.onReconnecting(callId, callException);
            }
        }

        @Override
        public void onReconnected(@NonNull Call call) {
            log.debug("Reconnected");

            // find call record
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));

            // notify observers
            for (Observer observer: observerList) {
                observer.onReconnected(callId);
            }
        }

        @Override
        public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
            log.debug("Disconnected: " + logException(callException));

            // find call record & remove
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));
            Objects.requireNonNull(callDatabase.remove(callId));

            // remove in-call notification
            ServiceCompat.stopForeground(
                    VoiceService.this, ServiceCompat.STOP_FOREGROUND_REMOVE);

            // notify observers
            for (Observer observer: observerList) {
                observer.onDisconnected(callId, callException);
            }

            if(activeCall != null) {
                activeCall = null;
            }
        }

        public void onCallQualityWarningsChanged(@NonNull Call call,
                                                 @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                                 @NonNull Set<Call.CallQualityWarning> previousWarnings) {
            log.debug("onCallQualityWarningsChanged");

            // find call record
            final UUID callId = Objects.requireNonNull(findAssociatedCallId(call));

            // notify observers
            for (Observer observer: observerList) {
                observer.onCallQualityWarningsChanged(callId, currentWarnings, previousWarnings);
            }
        }

        private String logException(@Nullable CallException callException) {
            return (null != callException)
                    ? format(Locale.US,
                    "%d, %s",
                            callException.getErrorCode(),
                            callException.getMessage())
                    : "";
        }
    };
}
