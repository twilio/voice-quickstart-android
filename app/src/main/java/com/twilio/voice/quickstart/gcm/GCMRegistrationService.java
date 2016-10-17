package com.twilio.voice.quickstart.gcm;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.twilio.voice.quickstart.R;
import com.twilio.voice.quickstart.VoiceActivity;

public class GCMRegistrationService extends IntentService {

    private static final String TAG = "GCMRegistration";

    public GCMRegistrationService() {
        super("GCMRegistrationService");
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
            sendGCMTokenToActivity(token);
        } catch (Exception e) {
            /*
             * If we are unable to retrieve the GCM token we notify the Activity
             * letting the user know this step failed.
             */
            Log.e(TAG, "Failed to retrieve GCM token", e);
            sendGCMTokenToActivity(null);
        }
    }

    /**
     * Send the GCM Token to the Voice Activity.
     *
     * @param gcmToken The new token.
     */
    private void sendGCMTokenToActivity(String gcmToken) {
        Intent intent = new Intent(VoiceActivity.ACTION_SET_GCM_TOKEN);
        intent.putExtra(VoiceActivity.KEY_GCM_TOKEN, gcmToken);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
