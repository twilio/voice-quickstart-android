package com.twilio.voice.quickstart;

import static com.twilio.voice.quickstart.VoiceApplication.voiceService;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


public class VoiceConnectionService extends ConnectionService {
    private static final Logger log = new Logger(VoiceConnectionService.class);
    private static final Map<UUID, Connection> connectionDatabase = new HashMap<>();
    private static final String CALL_RECIPIENT = "to";

    private static class VoiceConnection extends Connection {
        private static final Map<Integer, String> stateMappingTbl = new HashMap<>() {{
            put(STATE_INITIALIZING, "STATE_INITIALIZING");
            put(STATE_NEW, "STATE_NEW");
            put(STATE_RINGING, "STATE_RINGING");
            put(STATE_DIALING, "STATE_DIALING");
            put(STATE_ACTIVE, "STATE_ACTIVE");
            put(STATE_HOLDING, "STATE_HOLDING");
            put(STATE_DISCONNECTED, "STATE_DISCONNECTED");
            put(STATE_PULLING_CALL, "STATE_PULLING_CALL");
        }};

        private final UUID callId;

        public VoiceConnection(final UUID callID) {
            this.callId = callID;
        }

        @Override
        public void onStateChanged(int state) {
            log.debug("Connection:onStateChanged " + stateMappingTbl.get(state));
            if (STATE_DISCONNECTED == state) {
                // remove from db
                VoiceConnectionService.connectionDatabase.remove(callId);

                // destroy/release
                this.destroy();
            }
        }

        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            log.debug("Connection:onCallAudioStateChanged " + state);
        }

        @Override
        public void onPlayDtmfTone(char c) {
            log.debug("Connection:onPlayDtmfTone " + c);
        }

        @Override
        public void onDisconnect() {
            log.debug("Connection:onDisconnect");
            voiceService(voiceService -> voiceService.disconnectCall(callId));
        }

        @Override
        public void onSeparate() {
            log.debug("Connection:onSeparate");
        }

        @Override
        public void onAbort() {
            log.debug("Connection:onAbort");
            voiceService(voiceService -> voiceService.disconnectCall(callId));
        }

        @CallSuper
        @Override
        public void onAnswer() {
            log.debug("Connection:onAnswer");
            voiceService(voiceService -> voiceService.acceptCall(callId));
        }

        @Override
        public void onReject() {
            log.debug("Connection:onReject");
            voiceService(voiceService -> voiceService.rejectIncomingCall(callId));
        }

        @Override
        public void onHold() {
            log.debug("Connection:onHold");
            voiceService(voiceService -> voiceService.holdCall(callId));
        }

        @Override
        public void onUnhold() {
            log.debug("Connection:onUnhold");
            voiceService(voiceService -> voiceService.holdCall(callId));
        }

        @Override
        public void onPostDialContinue(boolean proceed) {
            log.debug("Connection:proceed " + proceed);
        }
    }

    private static class VoiceObserver implements VoiceService.Observer {
        private final Set<UUID> localDisconnectSet = new HashSet<>();
        private final PhoneAccountHandle phoneAccountHandle;
        private final Context appContext;

        public VoiceObserver(final Context context) {
            // register telecom account info
            appContext = context.getApplicationContext();
            String appName = appContext.getString(R.string.connection_service_name);
            phoneAccountHandle = new PhoneAccountHandle(
                    new ComponentName(appContext, VoiceConnectionService.class),
                    appName);
            TelecomManager telecomManager =
                    (TelecomManager)appContext.getSystemService(TELECOM_SERVICE);
            PhoneAccount phoneAccount = new PhoneAccount.Builder(phoneAccountHandle, appName)
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build();
            telecomManager.registerPhoneAccount(phoneAccount);
        }

        @Override
        public void connectCall(@NonNull UUID callId, @NonNull ConnectOptions options) {
            VoiceConnectionService.placeCall(
                    appContext,
                    callId,
                    Objects.requireNonNull(options.getParams().get("to")),
                    phoneAccountHandle);
        }

        @Override
        public void disconnectCall(@NonNull final UUID callId) {
            // mark that request was local
            localDisconnectSet.add(callId);
        }

        @Override
        public void acceptIncomingCall(@NonNull final UUID callId) {
            // does nothing
        }

        @Override
        public void rejectIncomingCall(@NonNull UUID callId) {
            VoiceConnectionService
                    .getConnection(callId)
                    .setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        }

        @Override
        public void incomingCall(@NonNull UUID callId, @NonNull CallInvite callInvite) {
            VoiceConnectionService.incomingCall(
                    appContext,
                    callId,
                    Objects.requireNonNull(callInvite.getFrom()),
                    phoneAccountHandle);
        }

        @Override
        public void cancelledCall(@NonNull UUID callId) {
            VoiceConnectionService
                    .getConnection(callId)
                    .setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
        }

        @Override
        public void muteCall(@NonNull final UUID callId, boolean isMuted) {
            // does nothing
        }

        @Override
        public void holdCall(@NonNull final UUID callId, boolean isOnHold) {
            // does nothing
        }

        @Override
        public void registrationSuccessful(@NonNull String fcmToken) {
            // does nothing
        }

        @Override
        public void registrationFailed(@NonNull RegistrationException registrationException) {
            // does nothing
        }

        @Override
        public void onRinging(@NonNull UUID callId) {
            // does nothing
        }

        @Override
        public void onConnectFailure(@NonNull UUID callId, @NonNull CallException callException) {
            VoiceConnectionService
                    .getConnection(callId)
                    .setDisconnected(new DisconnectCause(
                            DisconnectCause.ERROR,
                            callException.getMessage()));
        }

        @Override
        public void onConnected(@NonNull UUID callId) {
            VoiceConnectionService.getConnection(callId).setActive();
        }

        @Override
        public void onReconnecting(@NonNull UUID callId, @NonNull CallException callException) {
            // does nothing
        }

        @Override
        public void onReconnected(@NonNull UUID callId) {
            // does nothing
        }

        @Override
        public void onDisconnected(@NonNull UUID callId, @Nullable CallException callException) {
            if (null == callException) {
                if (localDisconnectSet.contains(callId)) {
                    VoiceConnectionService
                            .getConnection(callId)
                            .setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                    localDisconnectSet.remove(callId);
                } else {
                    VoiceConnectionService
                            .getConnection(callId)
                            .setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                }
            } else {
                VoiceConnectionService
                        .getConnection(callId)
                        .setDisconnected(new DisconnectCause(
                                DisconnectCause.ERROR,
                                callException.getMessage()));
            }
        }

        @Override
        public void onCallQualityWarningsChanged(@NonNull UUID callId, @NonNull Set<Call.CallQualityWarning> currentWarnings, @NonNull Set<Call.CallQualityWarning> previousWarnings) {
            // does nothing
        }
    }

    public enum AudioDevices {
        Earpiece,
        Speaker,
        Headset,
        Bluetooth
    }

    public static VoiceObserver getObserver(final Context context) {
        return new VoiceObserver(context);
    }

    public static void selectAudioDevice(@NonNull final UUID callId,
                                         @NonNull final AudioDevices audioDevice) {
        // find connection
        final Connection connection = Objects.requireNonNull(connectionDatabase.get(callId));

        // set audio routing
        switch (audioDevice) {
            case Speaker:
                connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
                break;
            case Earpiece:
                connection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
                break;
            case Headset:
                connection.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET);
                break;
            case Bluetooth:
                connection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
                break;
        }
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {
        // make android telephony connection
        Connection outgoingCallConnection = createConnection(request);
        outgoingCallConnection.setAddress(
                request.getExtras().getParcelable(CALL_RECIPIENT),
                TelecomManager.PRESENTATION_ALLOWED);
        outgoingCallConnection.setDialing();

        // store in db
        final UUID callId = (UUID) request.getExtras().getSerializable(Constants.CALL_UUID);
        connectionDatabase.put(callId, outgoingCallConnection);

        return outgoingCallConnection;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {
        // make android telephony connection
        Connection incomingCallConnection = createConnection(request);
        incomingCallConnection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        incomingCallConnection.setRinging();

        // store in db
        final UUID callId = (UUID) Objects.requireNonNull(
                request.getExtras().getSerializable(Constants.CALL_UUID));
        connectionDatabase.put(callId, incomingCallConnection);

        // make android telephony connection
        return incomingCallConnection;
    }

    @SuppressLint("MissingPermission")
    protected static void placeCall(@NonNull final Context context,
                                    @NonNull final UUID callId,
                                    @NonNull final String recipient,
                                    @NonNull final PhoneAccountHandle phoneAccountHandle) {
        log.debug("placeCall");

        if (arePermissionsGranted(context)) {
            final Uri recipientUri =
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, recipient, null);

            // invoke service
            Bundle extra = new Bundle();
            extra.putSerializable(Constants.CALL_UUID, callId);
            extra.putParcelable(CALL_RECIPIENT, recipientUri);

            Bundle telecomInfo = createTelephonyServiceBundle(phoneAccountHandle);
            telecomInfo.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extra);

            TelecomManager telecomMgr = (TelecomManager) context.getSystemService(TELECOM_SERVICE);
            telecomMgr.placeCall(recipientUri, telecomInfo);
        }
    }

    @SuppressLint("MissingPermission")
    protected static void incomingCall(@NonNull final Context context,
                                       @NonNull final UUID callId,
                                       @NonNull final String sender,
                                       @NonNull final PhoneAccountHandle phoneAccountHandle) {
        log.debug("incomingCall");

        if (arePermissionsGranted(context)) {
            // invoke service
            Bundle telecomInfo = createTelephonyServiceBundle(phoneAccountHandle);
            telecomInfo.putSerializable(Constants.CALL_UUID, callId);
            telecomInfo.putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, sender, null));

            TelecomManager telecomMgr = (TelecomManager) context.getSystemService(TELECOM_SERVICE);
            telecomMgr.addNewIncomingCall(phoneAccountHandle, telecomInfo);
        }
    }

    protected static Connection getConnection(@NonNull final UUID callId) {
        return Objects.requireNonNull(connectionDatabase.get(callId));
    }

    private Connection createConnection(ConnectionRequest request) {
        final UUID callId = (UUID) request.getExtras().getSerializable(Constants.CALL_UUID);
        Connection connection = new VoiceConnection(callId);

        // self managed isn't available before version O
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);

        // set mute & hold capability
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        connection.setConnectionCapabilities(Connection.CAPABILITY_HOLD);
        return connection;
    }

    private static boolean arePermissionsGranted(Context context) {
        return (PackageManager.PERMISSION_GRANTED ==
                ActivityCompat.checkSelfPermission(context, Manifest.permission.MANAGE_OWN_CALLS));
    }

    private static Bundle createTelephonyServiceBundle(
            @NonNull final PhoneAccountHandle phoneAccountHandle) {
        final Bundle telBundle = new Bundle();
        telBundle.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        telBundle.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
        return telBundle;
    }
}
