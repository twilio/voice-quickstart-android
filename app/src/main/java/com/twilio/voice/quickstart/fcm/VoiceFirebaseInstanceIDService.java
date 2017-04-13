package com.twilio.voice.quickstart.fcm;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.twilio.voice.quickstart.VoiceActivity;

public class VoiceFirebaseInstanceIDService extends FirebaseInstanceIdService {

    private static final String TAG = "VoiceFbIIDSvc";

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    // [START refresh_token]
    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // Notify Activity of FCM token
        Intent intent = new Intent(VoiceActivity.ACTION_FCM_TOKEN);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    // [END refresh_token]

}
