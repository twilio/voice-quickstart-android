package com.twilio.voice.quickstart;

import static com.twilio.voice.quickstart.Constants.ACTION_FCM_TOKEN;
import static com.twilio.voice.quickstart.Constants.ACTION_INCOMING_CALL;
import static com.twilio.voice.quickstart.Constants.CALL_SID;
import static com.twilio.voice.quickstart.Constants.FCM_TOKEN;
import static com.twilio.voice.quickstart.Constants.INCOMING_CALL_INVITE;
import static com.twilio.voice.quickstart.Constants.ACTION_CANCEL_CALL;
import static com.twilio.voice.quickstart.Constants.CANCELLED_CALL_INVITE;
import static java.lang.String.format;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Parcelable;
import android.util.Pair;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

public class IncomingMessageService extends FirebaseMessagingService implements MessageListener {
    private static final Logger log = new Logger(IncomingMessageService.class);

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        log.debug(format(
                "Received firebase message\n\tmessage data: %s\n\tfrom: %s",
                remoteMessage.getData(),
                remoteMessage.getFrom()));

        // Check if message contains a data payload.
        if (!remoteMessage.getData().isEmpty() && !Voice.handleMessage(this, remoteMessage.getData(), this)) {
            log.error(format("Received message was not a valid Twilio Voice SDK payload: %s", remoteMessage.getData()));
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        startVoiceService(ACTION_FCM_TOKEN, new Pair<>(FCM_TOKEN, token));
    }

    @Override
    public void onCallInvite(@NonNull CallInvite callInvite) {
        startVoiceService(
                ACTION_INCOMING_CALL,
                new Pair<>(INCOMING_CALL_INVITE, callInvite));
    }

    @Override
    public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite,
                                      @Nullable CallException callException) {
        startVoiceService(
                ACTION_CANCEL_CALL,
                new Pair<>(CANCELLED_CALL_INVITE, cancelledCallInvite));
    }

    @SafeVarargs
    private void startVoiceService(@NonNull final String action,
                                   @NonNull final Pair<String, Object>...data) {
        final Intent intent = new Intent(this, VoiceService.class);
        intent.setAction(action);
        for (Pair<String, Object> pair: data) {
            if (pair.second instanceof String) {
                intent.putExtra(pair.first, (String)pair.second);
            } else if (pair.second instanceof Parcelable) {
                intent.putExtra(pair.first, (Parcelable)pair.second);
            }
        }
        startService(intent);
    }
}
