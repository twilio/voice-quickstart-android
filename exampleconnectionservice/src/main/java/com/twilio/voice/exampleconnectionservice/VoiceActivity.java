package com.twilio.voice.exampleconnectionservice;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.telecom.Connection;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.Voice;

import java.util.HashMap;

import static java.util.ResourceBundle.getBundle;


public class VoiceActivity extends AppCompatActivity {
    private static final String TAG = "VoiceActivity";

    /*
    * You must provide a Twilio Access Token to connect to the Voice service
    */
    private static final String TWILIO_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImN0eSI6InR3aWxpby1mcGE7dj0xIn0.eyJpc3MiOiJTS2QwMjkwNWY0OTNiMzNiNTBmZDNlMzJmYmRmNDMyMTIxIiwiZ3JhbnRzIjp7InZvaWNlIjp7Im91dGdvaW5nIjp7ImFwcGxpY2F0aW9uX3NpZCI6IkFQMTk4ZmE1ZjE1NTQxMWNiOTY2NDkwMWU2YTczYWVmNTgifSwicHVzaF9jcmVkZW50aWFsX3NpZCI6IkNSMWE3NWMyYzY4YTA3ZGMzZGFiMmMxZDEzOTYzNDlmYzUifSwiaWRlbnRpdHkiOiJrdW1rdW0ifSwic3ViIjoiQUM5NmNjYzkwNDc1M2IzMzY0ZjI0MjExZThkOTc0NmE5MyIsImV4cCI6MTUxNjM5NTk4MywibmJmIjoxNTE2MzA5NTgzfQ.nmojecobX1ulPzdqNmSTnkZaeY25MdVIylSFmBgtahM";

    public static final String OUTGOING_CALL_ADDRESS = "OUTGOING_CALL_ADDRESS";

    public static final String ACTION_OUTGOING_CALL = "ACTION_OUTGOING_CALL";
    public static final String ACTION_DISCONNECT_CALL = "ACTION_DISCONNECT_CALL";
    public static final String ACTION_REJECT_CALL = "ACTION_REJECT_CALL";
    public static final String ACTION_DTMF_SEND = "ACTION_DTMF_SEND";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;
    private static final int CALL_PHONE_CODE = 2;
    private static final int SNACKBAR_DURATION = 4000;
    private CoordinatorLayout coordinatorLayout;

    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE};

    private TelecomManager telecomManager;
    private PhoneAccountHandle handle;
    private PhoneAccount phoneAccount;

    HashMap<String, String> twiMLParams = new HashMap<>();
    private AlertDialog alertDialog;
    Call activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
        coordinatorLayout = findViewById(R.id.coordinator_layout);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog = createCallDialog(callClickListener(), cancelCallClickListener(), VoiceActivity.this);
                alertDialog.show();
            }
        });

        /*
         * Ensure the microphone and BIND_TELECOM_CONNECTION_SERVICE permissions are enabled
         */
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            setupPhoneAccount();
            /*
             * Setup the broadcast receiver to be notified of FCM Token updates
             * or incoming call invite in this Activity.
             */
            voiceBroadcastReceiver = new VoiceBroadcastReceiver();
            registerReceiver();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_voice, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Setup
    void setupPhoneAccount() {
        String appName = this.getString(R.string.app_name);
        handle = new PhoneAccountHandle(new ComponentName(this.getApplicationContext(), VoiceConnectionService.class), appName);
        telecomManager = (TelecomManager) this.getApplicationContext().getSystemService(this.getApplicationContext().TELECOM_SERVICE);
        phoneAccount = new PhoneAccount.Builder(handle, appName)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();
        telecomManager.registerPhoneAccount(phoneAccount);
    }

    // Request required permissions
    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(coordinatorLayout,
                        "Microphone permissions needed. Please allow in your application settings.",
                        SNACKBAR_DURATION).show();
            } else {

            }
        } else if (requestCode == CALL_PHONE_CODE && permissions.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(coordinatorLayout,
                        "CALL_PHONE permissions needed. Please allow in your application settings.",
                        SNACKBAR_DURATION).show();
            } else {

            }
        }
    }

    //
    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_OUTGOING_CALL)) {
                /*
                 * Handle the incoming call invite
                 */
                handleCallRequest(intent);
            } else if (action.equals(ACTION_DISCONNECT_CALL)) {
                /*
                 * hangup the active call
                 */
                if (activeCall != null) {
                    activeCall.disconnect();
                }
            } else if (action.equals(ACTION_DTMF_SEND)) {
                /*
                 * send DTMF
                 */
                if (activeCall != null) {
                    String digits = intent.getStringExtra("DTMF");
                    activeCall.sendDigits(digits);
                }
            }
        }
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_OUTGOING_CALL);
            intentFilter.addAction(ACTION_DISCONNECT_CALL);
            intentFilter.addAction(ACTION_DTMF_SEND);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onConnectFailure(Call call, CallException error) {
                Log.d(TAG, "Connect failure");
                String message = String.format("Call Error: %d, %s", error.getErrorCode(), error.getMessage());
                Log.e(TAG, message);
                Snackbar.make(coordinatorLayout, message, SNACKBAR_DURATION).show();
                endCall();
            }

            @Override
            public void onConnected(Call call) {
                Log.d(TAG, "Connected");
            }

            @Override
            public void onDisconnected(Call call, CallException error) {
                Log.d(TAG, "Disconnected");
                if (error != null) {
                    String message = String.format("Call Error: %d, %s", error.getErrorCode(), error.getMessage());
                    Log.e(TAG, message);
                    Snackbar.make(coordinatorLayout, message, SNACKBAR_DURATION).show();
                }
                endCall();
            }
        };
    }

    private void handleCallRequest(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_OUTGOING_CALL)) {
                String contact = intent.getStringExtra(VoiceActivity.OUTGOING_CALL_ADDRESS);
                String[] contactparts = contact.split(":");
                twiMLParams.put("to", contactparts[1]);
                activeCall = Voice.call(VoiceActivity.this, TWILIO_ACCESS_TOKEN, twiMLParams, callListener());
            }
        }
    }

    private void makeCall(String to) {
        Log.d(TAG, "makeCall");
        Uri uri = Uri.fromParts("tel", to, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("to", to);
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        telecomManager.placeCall(uri, callInfo);
    }

    private void endCall() {
        Log.d(TAG, "endCall");
        Connection conn = VoiceConnectionService.getConnection();
        if (conn == null) {
            Snackbar.make(coordinatorLayout, "No call exists for you to end", SNACKBAR_DURATION).show();
        } else {
            DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
            conn.setDisconnected(cause);
            conn.destroy();
            VoiceConnectionService.deinitConnection();
        }
    }

    private DialogInterface.OnClickListener callClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Place a call
                EditText contact = (EditText) ((AlertDialog) dialog).findViewById(R.id.contact);
                twiMLParams.put("to", contact.getText().toString());
                // initiate the dialer
                makeCall(contact.getText().toString());
                alertDialog.dismiss();
            }
        };
    }

    private DialogInterface.OnClickListener cancelCallClickListener() {
        return new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                alertDialog.dismiss();
            }
        };
    }

    public static AlertDialog createCallDialog(final DialogInterface.OnClickListener callClickListener,
                                               final DialogInterface.OnClickListener cancelClickListener,
                                               final Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

        alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp);
        alertDialogBuilder.setTitle("Call");
        alertDialogBuilder.setPositiveButton("Call", callClickListener);
        alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener);
        alertDialogBuilder.setCancelable(false);

        LayoutInflater li = LayoutInflater.from(context);
        View dialogView = li.inflate(R.layout.dialog_call, null);
        final EditText contact = (EditText) dialogView.findViewById(R.id.contact);
        contact.setHint(R.string.callee);
        alertDialogBuilder.setView(dialogView);

        return alertDialogBuilder.create();

    }

}
