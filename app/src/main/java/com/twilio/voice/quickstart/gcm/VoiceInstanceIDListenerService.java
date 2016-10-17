package com.twilio.voice.quickstart.gcm;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.iid.InstanceIDListenerService;

public class VoiceInstanceIDListenerService extends InstanceIDListenerService {

    private static final String TAG = "VoiceGCMListenerService";

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        Log.d(TAG, "onTokenRefresh");

        Intent intent = new Intent(this, GCMRegistrationService.class);
        startService(intent);
    }
}
