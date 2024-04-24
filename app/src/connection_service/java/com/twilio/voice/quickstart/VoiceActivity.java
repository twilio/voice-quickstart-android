package com.twilio.voice.quickstart;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.DisconnectCause;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.EditText;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.messaging.FirebaseMessaging;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import android.telecom.PhoneAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

public class VoiceActivity extends AppCompatActivity {

    private static final String TAG = "VoiceActivity";
    public static final String ACTION_DISCONNECT_CALL = "ACTION_DISCONNECT_CALL";
    public static final String ACTION_DTMF_SEND = "ACTION_DTMF_SEND";
    public static final String DTMF = "DTMF";
    private static final int PERMISSIONS_ALL = 100;
    private final String accessToken = "PASTE_YOUR_ACCESS_TOKEN_HERE";

    private final List<AudioDevices> audioDevices = new ArrayList<>();

    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, never populated for the Quickstart
    HashMap<String, String> params = new HashMap<>();

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton callActionFab;
    private FloatingActionButton hangupActionFab;
    private FloatingActionButton holdActionFab;
    private FloatingActionButton muteActionFab;
    private Chronometer chronometer;

    private NotificationManager notificationManager;
    private AlertDialog alertDialog;
    private CallInvite activeCallInvite;
    private Call activeCall;
    private int activeCallNotificationId;

    private final BroadcastReceiver wiredHeadsetReceiver = wiredHeadsetReceiver();

    private final BroadcastReceiver bluetoothReceiver = bluetoothReceiver();

    RegistrationListener registrationListener = registrationListener();
    Call.Listener callListener = callListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);

        // These flags ensure that the activity can be launched when the screen is locked.
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


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

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of FCM Token updates
         * or incoming call invite in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        /*
         * Setup the UI
         */
        resetUI();

        /*
         * Displays a call dialog if the intent contains a call invite
         */
        handleIncomingCallIntent(getIntent());

        /*
         * Setup audio device management and set the volume control stream
         * Assume devices have speaker and earpiece
         */
        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        audioDevices.add(AudioDevices.Earpiece);
        audioDevices.add(AudioDevices.Speaker);
        boolean isBluetoothConnected = setupBluetooth();
        if (isBluetoothConnected) {
            audioDevices.add(AudioDevices.Bluetooth);
        }
        registerReceiver(wiredHeadsetReceiver, new IntentFilter(AudioManager.ACTION_HEADSET_PLUG));

        /*
         * Ensure required permissions are enabled
         */
        String[] permissionsList = providePermissions();
        if (!hasPermissions(this, permissionsList)) {
            ActivityCompat.requestPermissions(this, permissionsList, PERMISSIONS_ALL);
        } else {
            registerForCallInvites();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingCallIntent(intent);
    }

    static private String[] providePermissions() {
        List<String> permissionsList = new Vector<>() {{
            add(Manifest.permission.RECORD_AUDIO);
            //add(Manifest.permission.CALL_PHONE); // <- Add for different behavior
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
            put(Manifest.permission.CALL_PHONE, getString(R.string.call_permissions_rational));
            put(Manifest.permission.MANAGE_OWN_CALLS, getString(R.string.manage_call_permissions_rational));
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                put(Manifest.permission.BLUETOOTH_CONNECT, getString(R.string.bluetooth_permissions_rational));
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                put(Manifest.permission.POST_NOTIFICATIONS, getString(R.string.notification_permissions_rational));
            }
        }};
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
                Log.d(TAG, "Successfully registered FCM " + fcmToken);
            }

            @Override
            public void onError(@NonNull RegistrationException error,
                                @NonNull String accessToken,
                                @NonNull String fcmToken) {
                String message = String.format(
                        Locale.US,
                        "Registration Error: %d, %s",
                        error.getErrorCode(),
                        error.getMessage());
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            @Override
            public void onRinging(@NonNull Call call) {
                Log.d(TAG, "Ringing");
                /*
                 * When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
                 * is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
                 * the call is ringing and awaiting to be accepted on the callee's side. The application
                 * can use the `SoundPoolManager` to play custom audio files between the
                 * `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
                 */
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(VoiceActivity.this).playRinging();
                }
            }

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                Log.d(TAG, "Connect failure");
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(VoiceActivity.this).stopRinging();
                }
                resetConnectionService();
                String message = String.format(
                        Locale.US,
                        "Call Error: %d, %s",
                        error.getErrorCode(),
                        error.getMessage());
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
                resetUI();
            }

            @Override
            public void onConnected(@NonNull Call call) {
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(VoiceActivity.this).stopRinging();
                }
                Log.d(TAG, "Connected");
                activeCall = call;
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                Log.d(TAG, "onReconnecting");
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                Log.d(TAG, "onReconnected");
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                Log.d(TAG, "Disconnected");
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(VoiceActivity.this).stopRinging();
                }
                VoiceConnectionService.getConnection().setDisconnected(
                        new DisconnectCause(DisconnectCause.UNKNOWN));
                resetConnectionService();
                if (error != null) {
                    String message = String.format(
                            Locale.US,
                            "Call Error: %d, %s",
                            error.getErrorCode(),
                            error.getMessage());
                    Log.e(TAG, message);
                    Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
                }
                resetUI();
            }

            /*
             * currentWarnings: existing quality warnings that have not been cleared yet
             * previousWarnings: last set of warnings prior to receiving this callback
             *
             * Example:
             *   - currentWarnings: { A, B }
             *   - previousWarnings: { B, C }
             *
             * Newly raised warnings = currentWarnings - intersection = { A }
             * Newly cleared warnings = previousWarnings - intersection = { C }
             */
            public void onCallQualityWarningsChanged(@NonNull Call call,
                                                     @NonNull Set<Call.CallQualityWarning> currentWarnings,
                                                     @NonNull Set<Call.CallQualityWarning> previousWarnings) {

                if (previousWarnings.size() > 1) {
                    Set<Call.CallQualityWarning> intersection = new HashSet<>(currentWarnings);
                    currentWarnings.removeAll(previousWarnings);
                    intersection.retainAll(previousWarnings);
                    previousWarnings.removeAll(intersection);
                }

                String message = String.format(
                        Locale.US,
                        "Newly raised warnings: " + currentWarnings + " Clear warnings " + previousWarnings);
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG).show();
            }
        };
    }

    /*
     * The UI state when there is an active call
     */
    private void setCallUI() {
        callActionFab.hide();
        hangupActionFab.show();
        holdActionFab.show();
        muteActionFab.show();
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    /*
     * Reset UI elements
     */
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

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(bluetoothReceiver);
        unregisterReceiver(wiredHeadsetReceiver);
        setVolumeControlStream(savedVolumeControlStream);
        SoundPoolManager.getInstance(this).release();
        super.onDestroy();
    }

    private void handleIncomingCallIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            activeCallNotificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);

            switch (action) {
                case Constants.ACTION_INCOMING_CALL:
                    handleIncomingCall();
                    break;
                case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                    showIncomingCallDialog();
                    break;
                case Constants.ACTION_CANCEL_CALL:
                    handleCancel();
                    break;
                case Constants.ACTION_FCM_TOKEN:
                    registerForCallInvites();
                    break;
                case Constants.ACTION_ACCEPT:
                    answer();
                    break;
                default:
                    break;
            }
        }
    }

    private void handleIncomingCall() {
        if (isAppVisible()) {
            showIncomingCallDialog();
        }
    }

    private void handleCancel() {
        if (alertDialog != null && alertDialog.isShowing()) {
            SoundPoolManager.getInstance(this).stopRinging();
            alertDialog.cancel();
        }
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_DISCONNECT_CALL);
            intentFilter.addAction(ACTION_DTMF_SEND);
            intentFilter.addAction(Constants.ACTION_INCOMING_CALL);
            intentFilter.addAction(Constants.ACTION_OUTGOING_CALL);
            intentFilter.addAction(Constants.ACTION_CANCEL_CALL);
            intentFilter.addAction(Constants.ACTION_FCM_TOKEN);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceBroadcastReceiver);
            isReceiverRegistered = false;
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Constants.ACTION_OUTGOING_CALL:
                    handleCallRequest(intent);
                    break;
                case ACTION_DISCONNECT_CALL:
                    if (activeCall != null) {
                        activeCall.disconnect();
                    }
                    break;
                case ACTION_DTMF_SEND:
                    if (activeCall != null) {
                        activeCall.sendDigits(Objects.requireNonNull(intent.getStringExtra(DTMF)));
                    }
                    break;
            }
        }
    }

    private void handleCallRequest(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            final Bundle extras = intent.getExtras();
            final Uri recipient = extras.getParcelable(Constants.OUTGOING_CALL_RECIPIENT);
            params.put("to", recipient.getEncodedSchemeSpecificPart());
            ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                    .params(params)
                    .build();
            activeCall = Voice.connect(VoiceActivity.this, connectOptions, callListener);
        }
    }

    private void initiateCall(String to) {
        Intent intent = new Intent(VoiceActivity.this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_OUTGOING_CALL);
        intent.putExtra(Constants.OUTGOING_CALL_RECIPIENT,
                        Uri.fromParts(PhoneAccount.SCHEME_TEL, to, null));
        startService(intent);
    }

    private DialogInterface.OnClickListener answerCallClickListener() {
        return (dialog, which) -> {
            Log.d(TAG, "Clicked accept");
            Intent acceptIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
            acceptIntent.setAction(Constants.ACTION_ACCEPT);
            acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
            acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, activeCallNotificationId);
            Log.d(TAG, "Clicked accept startService");
            startService(acceptIntent);
        };
    }

    private DialogInterface.OnClickListener callClickListener() {
        return (dialog, which) -> {
            // Place a call
            EditText contact = (EditText) ((AlertDialog) dialog).findViewById(R.id.contact);
            // Initiate the dialer
            initiateCall(contact.getText().toString());
            alertDialog.dismiss();
        };
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return (dialogInterface, i) -> {
            SoundPoolManager.getInstance(VoiceActivity.this).stopRinging();
            if (activeCallInvite != null) {
                Intent intent = new Intent(VoiceActivity.this, IncomingCallNotificationService.class);
                intent.setAction(Constants.ACTION_REJECT);
                intent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
                startService(intent);
            }
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
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

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     */
    private void registerForCallInvites() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        return;
                    }
                    if (null != task.getResult()) {
                        String fcmToken = Objects.requireNonNull(task.getResult());
                        Log.i(TAG, "Registering with FCM");
                        Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
                    }
                });
    }

    private View.OnClickListener callActionFabClickListener() {
        return v -> {
            alertDialog = createCallDialog(callClickListener(), cancelCallClickListener(), VoiceActivity.this);
            alertDialog.show();
        };
    }

    private View.OnClickListener hangupActionFabClickListener() {
        return v -> {
            SoundPoolManager.getInstance(VoiceActivity.this).playDisconnect();
            resetUI();
            disconnect();

        };
    }

    private View.OnClickListener holdActionFabClickListener() {
        return v -> hold();
    }

    private View.OnClickListener muteActionFabClickListener() {
        return v -> mute();
    }

    /*
     * Accept an incoming Call
     */
    private void answer() {
        SoundPoolManager.getInstance(this).stopRinging();
        activeCallInvite.accept(this, callListener);
        notificationManager.cancel(activeCallNotificationId);
        stopService(new Intent(getApplicationContext(), IncomingCallNotificationService.class));
        setCallUI();
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    /*
     * Disconnect from Call
     */
    private void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    private void hold() {
        if (activeCall != null) {
           boolean hold = !activeCall.isOnHold();
           activeCall.hold(hold);
           applyFabState(holdActionFab, hold);

        }
    }

    private void mute() {
        if (activeCall != null) {
            boolean mute = !activeCall.isMuted();
            activeCall.mute(mute);
            applyFabState(muteActionFab, mute);
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
        registerForCallInvites();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);
        if (audioDevices.contains(AudioDevices.Bluetooth)) {
            updateAudioDeviceIcon(AudioDevices.Bluetooth);
        } else {
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

        for (AudioDevices device : audioDevices) {
            devices.add(device.name());
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setSingleChoiceItems(
                devices.toArray(new CharSequence[0]),
                0,
                (dialog, index) -> {
                    dialog.dismiss();
                    AudioDevices selectedDevice = audioDevices.get(index);
                    updateAudioDeviceIcon(selectedDevice);
                    VoiceConnectionService.selectAudioDevice(selectedDevice);
                    Collections.swap(audioDevices, 0, index);
                }).create().show();
    }

    /*
     * Update the menu icon based on the currently selected audio device.
     */
    private void updateAudioDeviceIcon(AudioDevices selectedAudioDevice) {
        int audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;
        if (selectedAudioDevice == AudioDevices.Bluetooth) {
            audioDeviceMenuIcon = R.drawable.ic_bluetooth_white_24dp;
        } else if (selectedAudioDevice == AudioDevices.Headset) {
            audioDeviceMenuIcon = R.drawable.ic_headset_mic_white_24dp;
        } else if (selectedAudioDevice == AudioDevices.Earpiece) {
            audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;
        } else if (selectedAudioDevice == AudioDevices.Speaker) {
            audioDeviceMenuIcon = R.drawable.ic_volume_up_white_24dp;
        }

        if (audioDeviceMenuItem != null) {
            audioDeviceMenuItem.setIcon(audioDeviceMenuIcon);
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

    private void showIncomingCallDialog() {
        SoundPoolManager.getInstance(this).playRinging();
        if (activeCallInvite != null) {
            alertDialog = createIncomingCallDialog(VoiceActivity.this,
                    activeCallInvite,
                    answerCallClickListener(),
                    cancelCallClickListener());
            alertDialog.show();
        }
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    protected enum AudioDevices {
        Earpiece,
        Speaker,
        Headset,
        Bluetooth
    }

    private BroadcastReceiver wiredHeadsetReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state", 0);
                if (state == 1) { // plugged
                    audioDevices.add(AudioDevices.Headset);
                    updateAudioDeviceIcon(AudioDevices.Headset);
                } else {
                    audioDevices.remove(AudioDevices.Headset);
                    if (audioDevices.contains(AudioDevices.Bluetooth)) {
                        updateAudioDeviceIcon(AudioDevices.Bluetooth);
                    } else {
                        updateAudioDeviceIcon(AudioDevices.Earpiece);
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
                        audioDevices.add(AudioDevices.Bluetooth);
                        updateAudioDeviceIcon(AudioDevices.Bluetooth);
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        audioDevices.remove(AudioDevices.Bluetooth);
                        if (audioDevices.contains(AudioDevices.Headset)) {
                            updateAudioDeviceIcon(AudioDevices.Headset);
                        } else {
                            updateAudioDeviceIcon(AudioDevices.Earpiece);
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

    private void resetConnectionService() {
        if (null != VoiceConnectionService.getConnection()) {
            VoiceConnectionService.releaseConnection();
        }
    }
}
