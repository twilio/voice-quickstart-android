package com.twilio.voice.quickstart;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.twilio.voice.quickstart.VoiceApplication.voiceService;
import static java.lang.String.format;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.messaging.FirebaseMessaging;
import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import kotlin.Unit;

public class VoiceActivity extends AppCompatActivity implements VoiceService.Observer {
    static final String accessToken = "PASTE_TOKEN_HERE";

    private static final Logger log = new Logger(VoiceActivity.class);
    private static final int PERMISSIONS_ALL = 100;
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton callActionFab;
    private FloatingActionButton hangupActionFab;
    private FloatingActionButton holdActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton dialpadFab;
    private Chronometer chronometer;
    private AlertDialog alertDialog;
    private UUID activeCallId;
    private UUID pendingCallId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log.debug("onCreate");

        super.onCreate(savedInstanceState);

        // These flags ensure that the activity can be launched when the screen is locked.
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // setup ui
        setContentView(R.layout.activity_voice);

        coordinatorLayout = findViewById(R.id.coordinator_layout);
        callActionFab = findViewById(R.id.call_action_fab);
        hangupActionFab = findViewById(R.id.hangup_action_fab);
        holdActionFab = findViewById(R.id.hold_action_fab);
        muteActionFab = findViewById(R.id.mute_action_fab);
        dialpadFab = findViewById(R.id.dialpad_fab);
        chronometer = findViewById(R.id.chronometer);

        callActionFab.setOnClickListener(callActionFabClickListener());
        hangupActionFab.setOnClickListener(hangupActionFabClickListener());
        holdActionFab.setOnClickListener(holdActionFabClickListener());
        muteActionFab.setOnClickListener(muteActionFabClickListener());
        dialpadFab.setOnClickListener(dialpadFabClickListener());

        // register with voice service
        voiceService(voiceService -> voiceService.registerObserver(this));

        // register incoming calls
        registerIncomingCalls();

        // handle incoming intents
        handleIntent(getIntent());

        // Setup audio device management and set the volume control stream
        audioSwitch = new AudioSwitch(getApplicationContext(), null, true);
        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        // Ensure required permissions are enabled
        String[] permissionsList = providePermissions();
        if (!hasPermissions(this, permissionsList)) {
            ActivityCompat.requestPermissions(this, permissionsList, PERMISSIONS_ALL);
        } else {
            startAudioSwitch();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        log.debug("onResume");
        super.onResume();

        // update ui
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    protected void onPause() {
        log.debug("onPause");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");

        // unregister with voice service
        voiceService(voiceService -> voiceService.unregisterObserver(this));

        // Tear down audio device management and restore previous volume stream
        audioSwitch.stop();
        setVolumeControlStream(savedVolumeControlStream);
        super.onDestroy();
    }

    @Override
    public void incomingCall(@NonNull final UUID callId, @NonNull final CallInvite invite) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void connectCall(@NonNull final UUID callId, @NonNull ConnectOptions options) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void disconnectCall(@NonNull final UUID callId) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void acceptIncomingCall(@NonNull final UUID callId) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void rejectIncomingCall(@NonNull final UUID callId) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void cancelledCall(@NonNull final UUID callId) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void muteCall(@NonNull final UUID callId, boolean isMuted) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void holdCall(@NonNull final UUID callId, boolean isOnHold) {
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void registrationSuccessful(@NonNull String fcmToken) {
        log.debug("Successfully registered FCM " + fcmToken);
    }

    @Override
    public void registrationFailed(@NonNull RegistrationException error) {
        String message = format(
                Locale.US,
                "Registration Error: %d, %s",
                error.getErrorCode(),
                error.getMessage());
        log.error(message);
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onRinging(@NonNull UUID callId) {
        // does nothing
    }

    @Override
    public void onConnectFailure(@NonNull UUID callId, @NonNull CallException error) {
        audioSwitch.deactivate();

        String message = format(
                Locale.US,
                "Call Error: %d, %s",
                error.getErrorCode(),
                error.getMessage());
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void onConnected(@NonNull UUID callId) {
        audioSwitch.activate();
    }

    @Override
    public void onReconnecting(@NonNull UUID callId, @NonNull CallException callException) {
        Snackbar.make(
                coordinatorLayout, "Call attempting reconnection", Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onReconnected(@NonNull UUID callId) {
        Snackbar.make(coordinatorLayout, "Call reconnected", Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDisconnected(@NonNull UUID callId, CallException error) {
        audioSwitch.deactivate();

        if (error != null) {
            String message = format(
                    Locale.US,
                    "Call Error: %d, %s",
                    error.getErrorCode(),
                    error.getMessage());
            Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
        }
        voiceService(voiceService -> updateUI(voiceService.getStatus()));
    }

    @Override
    public void onCallQualityWarningsChanged(@NonNull UUID callId,
                                             @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                             @NonNull Set<Call.CallQualityWarning> previousWarnings) {
        // currentWarnings: existing quality warnings that have not been cleared yet
        // previousWarnings: last set of warnings prior to receiving this callback
        //
        // Example:
        //  - currentWarnings: { A, B }
        //  - previousWarnings: { B, C }
        //
        // Newly raised warnings = currentWarnings - intersection = { A }
        // Newly cleared warnings = previousWarnings - intersection = { C }
        if (previousWarnings.size() > 1) {
            Set<Call.CallQualityWarning> intersection = new HashSet<>(currentWarnings);
            currentWarnings.removeAll(previousWarnings);
            intersection.retainAll(previousWarnings);
            previousWarnings.removeAll(intersection);
        }

        String message = format(
                Locale.US,
                "Newly raised warnings: " + currentWarnings + " Clear warnings " + previousWarnings);
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
    }

    static private String[] providePermissions() {
        List<String> permissionsList = new Vector<>() {{
            add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }};
        String[] list = new String[permissionsList.size()];
        return permissionsList.toArray(list);
    }


    private Map<String, String> providePermissionsMesageMap() {
        return new HashMap<>() {{
            put(Manifest.permission.RECORD_AUDIO, getString(R.string.audio_permissions_rational));
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                put(Manifest.permission.BLUETOOTH_CONNECT, getString(R.string.bluetooth_permissions_rational));
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                put(Manifest.permission.POST_NOTIFICATIONS, getString(R.string.notification_permissions_rational));
            }
        }};
    }

    private void updateUI(VoiceService.Status  status) {
        // if there are any active calls, show in-call UI
        if (status.callMap.isEmpty()) {
            // hide in-call buttons
            muteActionFab.hide();
            holdActionFab.hide();
            hangupActionFab.hide();
            dialpadFab.hide();
            chronometer.setVisibility(View.INVISIBLE);
            chronometer.stop();
            // show make-call ui
            callActionFab.show();
        } else {
            // hide make-call ui
            callActionFab.hide();
            // setup in-call ui
            muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                    VoiceActivity.this, R.drawable.ic_mic_white_24dp));
            holdActionFab.setBackgroundTintList(ColorStateList
                    .valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
            applyFabState(muteActionFab,
                    Objects.requireNonNull(status.callMap.get(status.activeCall)).isMuted);
            applyFabState(holdActionFab,
                    Objects.requireNonNull(status.callMap.get(status.activeCall)).onHold);
            // show ui
            hangupActionFab.show();
            holdActionFab.show();
            muteActionFab.show();
            dialpadFab.show();
            chronometer.setVisibility(View.VISIBLE);
            chronometer.setBase(
                    Objects.requireNonNull(status.callMap.get(status.activeCall)).timestamp);
            chronometer.start();
        }
        // if there are any pending calls, show incoming call dialog
        hideAlertDialog();
        if (!status.pendingCalls.isEmpty()) {
            // get the first call
            for (Map.Entry<UUID, CallInvite> entry : status.pendingCalls.entrySet()) {
                pendingCallId = entry.getKey();
                if ((Build.VERSION.SDK_INT < VERSION_CODES.O) || isAppVisible()) {
                    showIncomingCallDialog(entry.getValue());
                }
                break;
            }
        }
        // set active call
        activeCallId = status.activeCall;
    }

    private void handleIntent(final Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            pendingCallId = (UUID) intent.getSerializableExtra(Constants.CALL_UUID);
            switch (action) {
                case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                    voiceService(voiceService -> updateUI(voiceService.getStatus()));
                    break;
                case Constants.ACTION_ACCEPT_CALL:
                    answerCall(pendingCallId);
                    break;
                default:
                    break;
            }
        }
    }

    private DialogInterface.OnClickListener answerCallClickListener() {
        return (dialog, which) -> {
            log.debug("Clicked accept");
            answerCall(pendingCallId);
        };
    }

    private DialogInterface.OnClickListener rejectCallClickListener() {
        return (dialogInterface, i) ->
                voiceService(voiceService -> voiceService.rejectIncomingCall(pendingCallId));
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return (dialogInterface, i) -> {
            hideAlertDialog();
        };
    }

    private DialogInterface.OnClickListener callClickListener() {
        return (dialog, which) -> {
            // Place a call
            EditText contact = ((AlertDialog) dialog).findViewById(R.id.contact);
            final Map<String, String> params = new HashMap<>() {{
                put("to", contact.getText().toString());
            }};
            ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                    .params(params)
                    .build();
            hideAlertDialog();
            voiceService(voiceService -> activeCallId = voiceService.connectCall(connectOptions));
        };
    }

    public static AlertDialog createIncomingCallDialog(
            Context context,
            CallInvite callInvite,
            DialogInterface.OnClickListener answerCallClickListener,
            DialogInterface.OnClickListener cancelClickListener) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp);
        alertDialogBuilder.setTitle("Incoming Call");
        alertDialogBuilder.setPositiveButton("Accept", answerCallClickListener);
        alertDialogBuilder.setNegativeButton("Reject", cancelClickListener);
        alertDialogBuilder.setMessage(callInvite.getFrom() + " is calling with " + callInvite.getCallerInfo().isVerified() + " status");
        return alertDialogBuilder.create();
    }


    private View.OnClickListener callActionFabClickListener() {
        return v -> {
            alertDialog = createCallDialog(
                    callClickListener(), cancelCallClickListener(), VoiceActivity.this);
            alertDialog.show();
        };
    }

    private View.OnClickListener hangupActionFabClickListener() {
        return v -> voiceService(voiceService -> voiceService.disconnectCall(activeCallId));
    }

    private View.OnClickListener holdActionFabClickListener() {
        return v -> hold();
    }

    private View.OnClickListener muteActionFabClickListener() {
        return v -> mute();
    }
    private View.OnClickListener dialpadFabClickListener() {
        return v -> {
            Intent intent = new Intent(this, DialpadActivity.class);
            intent.putExtra("activeCallId", activeCallId);
            startActivity(intent);
        };
    }

    private void answerCall(final UUID callId) {
        voiceService(voiceService -> activeCallId = voiceService.acceptCall(callId));
    }

    private void hold() {
        if (activeCallId != null) {
            voiceService(voiceService -> voiceService.holdCall(activeCallId));
        }
    }

    private void mute() {
        if (activeCallId != null) {
            voiceService(voiceService -> voiceService.muteCall(activeCallId));
        }
    }

    private void applyFabState(FloatingActionButton button, boolean enabled) {
        // Set fab as pressed when call is on hold
        ColorStateList colorStateList = enabled ?
                ColorStateList.valueOf(ContextCompat.getColor(this,
                        R.color.colorPrimaryDark)) :
                ColorStateList.valueOf(ContextCompat.getColor(this,
                        R.color.colorAccent));
        button.setBackgroundTintList(colorStateList);
    }

    private static boolean hasPermissions(Context context, final String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(context, permission)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final Map<String, String> permissionsMessageMap = providePermissionsMesageMap();
        for (String permission : permissions) {
            if (!hasPermissions(this, permission)) {
                Snackbar.make(
                        coordinatorLayout,
                        Objects.requireNonNull(permissionsMessageMap.get(permission)),
                        Snackbar.LENGTH_LONG).show();
            }
        }
        startAudioSwitch();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_audio_device) {
            showAudioDevices();
            return true;
        }
        return false;
    }

    // Show the current available audio devices.
    private void showAudioDevices() {
        AudioDevice selectedDevice = audioSwitch.getSelectedAudioDevice();
        List<AudioDevice> availableAudioDevices = audioSwitch.getAvailableAudioDevices();

        if (selectedDevice != null) {
            int selectedDeviceIndex = availableAudioDevices.indexOf(selectedDevice);

            ArrayList<String> audioDeviceNames = new ArrayList<>();
            for (AudioDevice a : availableAudioDevices) {
                audioDeviceNames.add(a.getName());
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_device)
                    .setSingleChoiceItems(
                            audioDeviceNames.toArray(new CharSequence[0]),
                            selectedDeviceIndex,
                            (dialog, index) -> {
                                dialog.dismiss();
                                AudioDevice selectedAudioDevice = availableAudioDevices.get(index);
                                updateAudioDeviceIcon(selectedAudioDevice);
                                audioSwitch.selectDevice(selectedAudioDevice);
                            }).create().show();
        }
    }

    // Update the menu icon based on the currently selected audio device.
    private void updateAudioDeviceIcon(AudioDevice selectedAudioDevice) {
        if (audioDeviceMenuItem != null) {
            if (selectedAudioDevice instanceof AudioDevice.BluetoothHeadset) {
                audioDeviceMenuItem.setIcon(R.drawable.ic_bluetooth_white_24dp);
            } else if (selectedAudioDevice instanceof AudioDevice.WiredHeadset) {
                audioDeviceMenuItem.setIcon(R.drawable.ic_headset_mic_white_24dp);
            } else if (selectedAudioDevice instanceof AudioDevice.Earpiece) {
                audioDeviceMenuItem.setIcon(R.drawable.ic_phonelink_ring_white_24dp);
            } else if (selectedAudioDevice instanceof AudioDevice.Speakerphone) {
                audioDeviceMenuItem.setIcon(R.drawable.ic_volume_up_white_24dp);
            } else {
                audioDeviceMenuItem.setIcon(R.drawable.ic_phonelink_ring_white_24dp);
            }
        }
    }

    private static AlertDialog createCallDialog(final DialogInterface.OnClickListener callClickListener,
                                               final DialogInterface.OnClickListener cancelClickListener,
                                               final Activity activity) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

        alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp);
        alertDialogBuilder.setTitle("Call");
        alertDialogBuilder.setPositiveButton("Call", callClickListener);
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);

        LayoutInflater li = LayoutInflater.from(activity);
        View dialogView = li.inflate(
                R.layout.dialog_call,
                activity.findViewById(android.R.id.content),
                false);
        final EditText contact = dialogView.findViewById(R.id.contact);
        contact.setHint(R.string.callee);
        alertDialogBuilder.setView(dialogView);

        return alertDialogBuilder.create();

    }

    private void showIncomingCallDialog(@NonNull final CallInvite callInvite) {
        alertDialog = createIncomingCallDialog(VoiceActivity.this,
                callInvite,
                answerCallClickListener(),
                rejectCallClickListener());
        alertDialog.show();
    }

    private void hideAlertDialog() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
            alertDialog = null;
        }
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    private void startAudioSwitch() {
        // Start the audio device selector after the menu is created and update the icon when the
        // selected audio device changes.
        audioSwitch.start((audioDevices, audioDevice) -> {
            log.debug("Updating AudioDeviceIcon");
            updateAudioDeviceIcon(audioDevice);
            return Unit.INSTANCE;
        });
    }

    private void registerIncomingCalls() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        voiceService(voiceService ->
                                voiceService.registerFCMToken(task.getResult()));
                    } else {
                        log.error("FCM token retrieval failed: " +
                                Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
    }
}
