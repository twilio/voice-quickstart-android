package com.twilio.voice.quickstart.gcm;


import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.twilio.voice.quickstart.R;
import com.twilio.voice.quickstart.VoiceActivity;

public class RegistrationIntentService extends IntentService {

    private static final String TAG = "RegistrationIntentSvc";

    public RegistrationIntentService() {
        super("RegistrationIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            InstanceID instanceID = InstanceID.getInstance(this);
            String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId),
                    GoogleCloudMessaging.INSTANCE_ID_SCOPE,
                    null);
            sendRegistrationToVoiceClient(token);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve GCM token", e);
            sendRegistrationToVoiceClient(null);
        }
    }

    /**
     * Persist registration to Voice Application .
     *
     * @param token The new token.
     */
    private void sendRegistrationToVoiceClient(String token) {
        Intent setGcmToken = new Intent(VoiceActivity.ACTION_SET_GCM_TOKEN);
        setGcmToken.putExtra(VoiceActivity.KEY_GCM_TOKEN, token);
        LocalBroadcastManager.getInstance(this).sendBroadcast(setGcmToken);
    }
}
