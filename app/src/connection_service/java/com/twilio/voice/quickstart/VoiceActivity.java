package com.twilio.voice.quickstart;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static java.lang.String.format;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
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
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

public class VoiceActivity extends AppCompatActivity implements VoiceService.Observer  {

    private static final Logger log = new Logger(VoiceActivity.class);
    private static final int PERMISSIONS_ALL = 100;
    private final String accessToken = "PASTE_TOKEN_HERE";

    private final List<VoiceConnectionService.AudioDevices> audioDevices = new ArrayList<>();
    private final BroadcastReceiver wiredHeadsetReceiver = wiredHeadsetReceiver();
    private final BroadcastReceiver bluetoothReceiver = bluetoothReceiver();
    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton callActionFab;
    private FloatingActionButton hangupActionFab;
    private FloatingActionButton holdActionFab;
    private FloatingActionButton muteActionFab;
    private Chronometer chronometer;

    private AlertDialog alertDialog;
    private UUID activeCallId;
    private ServiceConnectionManager voiceService;

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
        chronometer = findViewById(R.id.chronometer);

        callActionFab.setOnClickListener(callActionFabClickListener());
        hangupActionFab.setOnClickListener(hangupActionFabClickListener());
        holdActionFab.setOnClickListener(holdActionFabClickListener());
        muteActionFab.setOnClickListener(muteActionFabClickListener());

        resetUI();

        // create voice service binding agent
        voiceService = new ServiceConnectionManager(this, accessToken, this);

        // register incoming calls
        registerIncomingCalls();

        // handle incoming intents
        handleIntent(getIntent());

        // Ensure required permissions are enabled
        String[] permissionsList = providePermissions();
        if (!hasPermissions(this, permissionsList)) {
            ActivityCompat.requestPermissions(this, permissionsList, PERMISSIONS_ALL);
        } else {
            setupAudioDeviceManagement();
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
    }

    @Override
    protected void onPause() {
        log.debug("onPause");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");

        // unbind from service
        voiceService.unbind();

        // unregister from receivers
        unregisterReceiver(bluetoothReceiver);
        unregisterReceiver(wiredHeadsetReceiver);

        // restore previous volume stream
        setVolumeControlStream(savedVolumeControlStream);
        super.onDestroy();
    }

    @Override
    public void incomingCall(@NonNull final UUID callId, @NonNull final CallInvite invite) {
        activeCallId = callId;
        if (isAppVisible()) {
            showIncomingCallDialog(invite);
        }
    }

    @Override
    public void connectCall(@NonNull UUID callId, @NonNull ConnectOptions options) {
        // does nothing
    }

    @Override
    public void disconnectCall(@NonNull UUID callId) {
        resetUI();
    }

    @Override
    public void acceptIncomingCall(@NonNull final UUID callId) {
        setCallUI();
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    @Override
    public void rejectIncomingCall(@NonNull final UUID callId) {
        cancelledCall(callId);
    }

    @Override
    public void cancelledCall(@NonNull final UUID callId) {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.cancel();
        }
        activeCallId = null;
    }

    @Override
    public void muteCall(@NonNull final UUID callId, boolean isMuted) {
        applyFabState(muteActionFab, isMuted);
    }

    @Override
    public void holdCall(@NonNull final UUID callId, boolean isOnHold) {
        applyFabState(holdActionFab, isOnHold);
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
        String message = format(
                Locale.US,
                "Call Error: %d, %s",
                error.getErrorCode(),
                error.getMessage());
        Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
        resetUI();
        activeCallId = null;
    }

    @Override
    public void onConnected(@NonNull UUID callId) {
        // does nothing
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
        if (error != null) {
            String message = format(
                    Locale.US,
                    "Call Error: %d, %s",
                    error.getErrorCode(),
                    error.getMessage());
            Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
        }
        resetUI();
        activeCallId = null;
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
            add(Manifest.permission.MANAGE_OWN_CALLS);
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
            put(Manifest.permission.MANAGE_OWN_CALLS, getString(R.string.manage_call_permissions_rational));
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                put(Manifest.permission.BLUETOOTH_CONNECT, getString(R.string.bluetooth_permissions_rational));
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                put(Manifest.permission.POST_NOTIFICATIONS, getString(R.string.notification_permissions_rational));
            }
        }};
    }

    // The UI state when there is an active call
    private void setCallUI() {
        callActionFab.hide();
        hangupActionFab.show();
        holdActionFab.show();
        muteActionFab.show();
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    // Reset UI elements
    private void resetUI() {
        callActionFab.show();
        muteActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_mic_white_24dp));
        holdActionFab.hide();
        holdActionFab.setBackgroundTintList(ColorStateList
                .valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
        muteActionFab.hide();
        hangupActionFab.hide();
        chronometer.setVisibility(View.INVISIBLE);
        chronometer.stop();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            activeCallId = (UUID) intent.getSerializableExtra(Constants.CALL_UUID);

            switch (action) {
                case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                    showIncomingCallDialog(
                            Objects.requireNonNull(
                                    intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE)));
                    break;
                case Constants.ACTION_ACCEPT_CALL:
                    answerCall();
                    break;
                default:
                    break;
            }
        }
    }

    private DialogInterface.OnClickListener answerCallClickListener() {
        return (dialog, which) -> {
            log.debug("Clicked accept");
            answerCall();
        };
    }

    private DialogInterface.OnClickListener rejectCallClickListener() {
        return (dialogInterface, i) -> voiceService.invoke(
                voiceService -> voiceService.rejectIncomingCall(activeCallId));
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return (dialogInterface, i) -> {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
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
            voiceService.invoke(
                    voiceService -> activeCallId = voiceService.connectCall(connectOptions));
            setCallUI();
            alertDialog.dismiss();
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
        return v -> voiceService.invoke(voiceService -> voiceService.disconnectCall(activeCallId));
    }

    private View.OnClickListener holdActionFabClickListener() {
        return v -> hold();
    }

    private View.OnClickListener muteActionFabClickListener() {
        return v -> mute();
    }

    private void answerCall() {
        voiceService.invoke(voiceService -> voiceService.acceptCall(activeCallId));
    }

    private void hold() {
        if (activeCallId != null) {
            voiceService.invoke(voiceService -> voiceService.holdCall(activeCallId));
        }
    }

    private void mute() {
        if (activeCallId != null) {
            voiceService.invoke(voiceService -> voiceService.muteCall(activeCallId));
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
        setupAudioDeviceManagement();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);
        if (audioDevices.contains(VoiceConnectionService.AudioDevices.Bluetooth)) {
            updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Bluetooth);
        } else if (!audioDevices.isEmpty()) {
            updateAudioDeviceIcon(audioDevices.get(audioDevices.size() - 1));
        }

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

    /*
     * Show the current available audio devices.
     */
    private void showAudioDevices() {
        List<String> devices = new ArrayList<>();

        for (VoiceConnectionService.AudioDevices device : audioDevices) {
            devices.add(device.name());
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setSingleChoiceItems(
                devices.toArray(new CharSequence[0]),
                0,
                (dialog, index) -> {
                    dialog.dismiss();
                    VoiceConnectionService.AudioDevices selectedDevice = audioDevices.get(index);
                    updateAudioDeviceIcon(selectedDevice);
                    VoiceConnectionService.selectAudioDevice(activeCallId, selectedDevice);
                    Collections.swap(audioDevices, 0, index);
                }).create().show();
    }

    // Update the menu icon based on the currently selected audio device.
    private void updateAudioDeviceIcon(VoiceConnectionService.AudioDevices selectedAudioDevice) {
        if (audioDeviceMenuItem != null) {
            switch (selectedAudioDevice) {
                case Bluetooth:
                    audioDeviceMenuItem.setIcon(R.drawable.ic_bluetooth_white_24dp);
                    break;
                case Headset:
                    audioDeviceMenuItem.setIcon(R.drawable.ic_headset_mic_white_24dp);
                    break;
                case Speaker:
                    audioDeviceMenuItem.setIcon(R.drawable.ic_volume_up_white_24dp);
                    break;
                default:
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

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    private BroadcastReceiver wiredHeadsetReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state", 0);
                if (state == 1) { // plugged
                    audioDevices.add(VoiceConnectionService.AudioDevices.Headset);
                    updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Headset);
                } else {
                    audioDevices.remove(VoiceConnectionService.AudioDevices.Headset);
                    if (audioDevices.contains(VoiceConnectionService.AudioDevices.Bluetooth)) {
                        updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Bluetooth);
                    } else {
                        updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Earpiece);
                    }
                }
            }
        };
    }

    private BroadcastReceiver bluetoothReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_CONNECTED:
                        audioDevices.add(VoiceConnectionService.AudioDevices.Bluetooth);
                        updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Bluetooth);
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        audioDevices.remove(VoiceConnectionService.AudioDevices.Bluetooth);
                        if (audioDevices.contains(VoiceConnectionService.AudioDevices.Headset)) {
                            updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Headset);
                        } else {
                            updateAudioDeviceIcon(VoiceConnectionService.AudioDevices.Earpiece);
                        }
                        break;
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private boolean setupBluetooth() {
        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, intentFilter);
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()
                && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED;
    }

    private void setupAudioDeviceManagement() {
        /*
         * Setup audio device management and set the volume control stream
         * Assume devices have speaker and earpiece
         */
        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        audioDevices.add(VoiceConnectionService.AudioDevices.Earpiece);
        audioDevices.add(VoiceConnectionService.AudioDevices.Speaker);
        boolean isBluetoothConnected = setupBluetooth();
        if (isBluetoothConnected) {
            audioDevices.add(VoiceConnectionService.AudioDevices.Bluetooth);
        }
        registerReceiver(wiredHeadsetReceiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));
    }

    private void registerIncomingCalls() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        voiceService.invoke(
                                voiceService -> voiceService.registerFCMToken(task.getResult()));
                    } else {
                        log.error("FCM token retrieval failed: " +
                                Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
    }

    private static class ServiceConnectionManager {
        private VoiceService voiceService = null;
        private final List<Task> pendingTasks = new LinkedList<>();
        private final String accessToken;
        private final Context context;
        private final VoiceService.Observer observer;
        private final ServiceConnection serviceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                // verify is main thread, all Voice SDK calls must be made on the same Looper thread
                assert(Looper.myLooper() == Looper.getMainLooper());
                // link to voice service
                voiceService = ((VoiceService.VideoServiceBinder)service).getService();
                voiceService.registerObserver(observer);
                // run tasks
                synchronized(ServiceConnectionManager.this) {
                    for (Task task : pendingTasks) {
                        task.run(voiceService);
                    }
                    pendingTasks.clear();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                voiceService = null;
            }
        };

        public interface Task {
            void run(final VoiceService voiceService);
        }

        public ServiceConnectionManager(final Context context,
                                        final String accessToken,
                                        final VoiceService.Observer observer) {
            this.context = context;
            this.accessToken = accessToken;
            this.observer = observer;
        }

        public void unbind() {
            if (null != voiceService) {
                voiceService.unregisterObserver(observer);
                context.unbindService(serviceConnection);
            }
        }

        public void invoke(Task task) {
            if (null != voiceService) {
                // verify is main thread, all Voice SDK calls must be made on the same Looper thread
                assert(Looper.myLooper() == Looper.getMainLooper());
                // run task
                synchronized (this) {
                    task.run(voiceService);
                }
            } else {
                // queue runnable
                pendingTasks.add(task);
                // bind to service
                Intent intent = new Intent(context, VoiceService.class);
                intent.putExtra(Constants.ACCESS_TOKEN, accessToken);
                intent.putExtra(Constants.CUSTOM_RINGBACK, BuildConfig.playCustomRingback);
                context.bindService(
                        intent,
                        serviceConnection,
                        BIND_AUTO_CREATE);
            }
        }
    }
}
