package com.twilio.voice.quickstart;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.VoiceClient;
import com.twilio.voice.quickstart.gcm.GCMRegistrationService;

import java.util.HashMap;

public class VoiceActivity extends AppCompatActivity {

    private static final String TAG = "VoiceActivity";

    /*
     * You must provide a Twilio Access Token to connect to the Voice service
     */
    private static final String TWILIO_ACCESS_TOKEN = "TWILIO_ACCESS_TOKEN";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private boolean speakerPhone;
    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;

    private boolean isReceiverRegistered;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, never populated for the Quickstart
    HashMap<String, String> twiMLParams = new HashMap<>();

    private CoordinatorLayout coordinatorLayout;
    private FloatingActionButton callActionFab;
    private FloatingActionButton hangupActionFab;
    private FloatingActionButton speakerActionFab;
    private Chronometer chronometer;

    public static final String ACTION_SET_GCM_TOKEN = "SET_GCM_TOKEN";
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_INCOMING_CALL = "INCOMING_CALL";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";

    private NotificationManager notificationManager;
    private String gcmToken;
    private AlertDialog alertDialog;
    private CallInvite activeCallInvite;
    private Call activeCall;

    RegistrationListener registrationListener = registrationListener();
    Call.Listener callListener = callListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        callActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        hangupActionFab = (FloatingActionButton) findViewById(R.id.hangup_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speakerphone_action_fab);
        chronometer = (Chronometer) findViewById(R.id.chronometer);

        callActionFab.setOnClickListener(callActionFabClickListener());
        hangupActionFab.setOnClickListener(hangupActionFabClickListener());
        speakerActionFab.setOnClickListener(speakerphoneActionFabClickListener());

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call invite in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        /*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Displays a call dialog if the intent contains a call invite
         */
        handleIncomingCallIntent(getIntent());

        /*
         * Ensure the microphone permission is enabled
         */
        if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        } else {
            startGCMRegistration();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingCallIntent(intent);
    }

    private void startGCMRegistration() {
        if (checkPlayServices()) {
            Intent intent = new Intent(this, GCMRegistrationService.class);
            startService(intent);
        }
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(String accessToken, String gcmToken) {
                Log.d(TAG, "Successfully registered");
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String gcmToken) {
                Log.e(TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onConnected(Call call) {
                Log.d(TAG, "Connected");
                activeCall = call;
            }

            @Override
            public void onDisconnected(Call call) {
                resetUI();
                Log.d(TAG, "Disconnect");
            }

            @Override
            public void onDisconnected(Call call, CallException error) {
                resetUI();
                Log.e(TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
            }
        };
    }

    /*
     * The UI state when there is an active call
     */
    private void setCallUI() {
        callActionFab.hide();
        hangupActionFab.show();
        speakerActionFab.show();
        chronometer.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
    }

    /*
     * Reset UI elements
     */
    private void resetUI() {
        speakerPhone = false;
        audioManager.setSpeakerphoneOn(speakerPhone);
        setAudioFocus(speakerPhone);
        speakerActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_volume_down_white_24px));
        speakerActionFab.hide();
        callActionFab.show();
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceBroadcastReceiver);
        isReceiverRegistered = false;
    }

    @Override
    public void onDestroy() {
        SoundPoolManager.getInstance(this).release();
        super.onDestroy();
    }

    private void handleIncomingCallIntent(Intent intent) {

        if (intent != null && intent.getAction() != null && intent.getAction() == ACTION_INCOMING_CALL) {
            activeCallInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
            if (!activeCallInvite.isCancelled()) {
                SoundPoolManager.getInstance(this).playRinging();
                alertDialog = createIncomingCallDialog(VoiceActivity.this,
                        activeCallInvite,
                        answerCallClickListener(),
                        cancelCallClickListener());
                alertDialog.show();
                notificationManager.cancel(intent.getIntExtra(INCOMING_CALL_NOTIFICATION_ID, 0));
            } else {
                if (alertDialog != null && alertDialog.isShowing()) {
                    SoundPoolManager.getInstance(this).stopRinging();
                    alertDialog.cancel();
                }
            }
        }
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_SET_GCM_TOKEN);
            intentFilter.addAction(ACTION_INCOMING_CALL);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_SET_GCM_TOKEN)) {
                String gcmToken = intent.getStringExtra(KEY_GCM_TOKEN);
                Log.i(TAG, "GCM Token : " + gcmToken);
                VoiceActivity.this.gcmToken = gcmToken;
                if(gcmToken == null) {
                    Snackbar.make(coordinatorLayout,
                            "Failed to get GCM Token. Unable to receive calls",
                            Snackbar.LENGTH_LONG).show();
                }
                callActionFab.show();
                if (VoiceActivity.this.gcmToken != null) {
                    register();
                }
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                /*
                 * Handle the incoming call invite
                 */
                handleIncomingCallIntent(intent);
            }
        }
    }

    private DialogInterface.OnClickListener answerCallClickListener() {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                answer();
                setCallUI();
                alertDialog.dismiss();
            }
        };
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                activeCallInvite.reject(VoiceActivity.this);
                alertDialog.dismiss();
            }
        };
    }

    public static AlertDialog createIncomingCallDialog(Context context, CallInvite callInvite, DialogInterface.OnClickListener answerCallClickListener, DialogInterface.OnClickListener cancelClickListener) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp);
        alertDialogBuilder.setTitle("Incoming Call");
        alertDialogBuilder.setPositiveButton("Accept", answerCallClickListener);
        alertDialogBuilder.setNegativeButton("Reject", cancelClickListener);
        alertDialogBuilder.setMessage(callInvite.getFrom() + " is calling.");
        return alertDialogBuilder.create();
    }

    /*
     * Register your GCM token with Twilio to enable receiving incoming calls via GCM
     */
    private void register() {
        VoiceClient.register(getApplicationContext(), TWILIO_ACCESS_TOKEN, gcmToken, registrationListener);
    }

    private View.OnClickListener callActionFabClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeCall = VoiceClient.call(getApplicationContext(), TWILIO_ACCESS_TOKEN, twiMLParams, callListener);
                setCallUI();
            }
        };
    }

    private View.OnClickListener hangupActionFabClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SoundPoolManager.getInstance(VoiceActivity.this).playDisconnect();
                resetUI();
                disconnect();
            }
        };
    }

    private View.OnClickListener speakerphoneActionFabClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSpeakerPhone();
            }
        };
    }

    /*
     * Accept an incoming Call
     */
    private void answer() {
        activeCallInvite.accept(VoiceActivity.this, callListener);
    }

    /*
     * Disconnect an active Call
     */
    private void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    private void toggleSpeakerPhone() {
        speakerPhone = !speakerPhone;

        setAudioFocus(speakerPhone);
        audioManager.setSpeakerphoneOn(speakerPhone);

        if(speakerPhone) {
            speakerActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_volume_mute_white_24px));
        } else {
            speakerActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_volume_down_white_24px));
        }
    }

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (resultMic == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(coordinatorLayout,
                    "Microphone permissions needed. Please allow in your application settings.",
                    Snackbar.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MIC_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        /*
         * Check if microphone permissions is granted
         */
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            boolean granted = true;
            if (granted) {
                startGCMRegistration();
            } else {
                Snackbar.make(coordinatorLayout,
                        "Microphone permissions needed. Please allow in your application settings.",
                        Snackbar.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.e(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
}
