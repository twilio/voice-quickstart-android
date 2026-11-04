package com.twilio.examplecustomlogger;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.twilio.voice.CallInvite;

public class CallActionReceiver extends BroadcastReceiver {
    static final String ACTION_DECLINE = "com.twilio.examplecustomlogger.CALL_DECLINE";

    @Override
    public void onReceive(Context context, Intent intent) {
        CallInvite invite = IncomingCallService.getInvite(intent);
        if (invite != null) {
            invite.reject(context);
        }
        context.getSystemService(NotificationManager.class)
                .cancel(IncomingCallService.NOTIFICATION_ID);
        context.sendBroadcast(
                new Intent(IncomingCallService.ACTION_CANCELLED).setPackage(context.getPackageName()));
    }
}
