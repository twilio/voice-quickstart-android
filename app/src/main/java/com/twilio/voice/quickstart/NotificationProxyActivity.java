package com.twilio.voice.quickstart;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.Voice;

public class NotificationProxyActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    handleIntent(getIntent());
    finish();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIntent(intent);
    finish();
  }

  private void handleIntent(Intent intent) {
    final String action = intent.getAction();
    if (action != null) {
      final Intent serviceIntent =
              (new Intent(intent)).setClass(this, IncomingCallNotificationService.class);
      final Intent appIntent =
              (new Intent(intent)).setClass(this, VoiceActivity.class);
      switch (action) {
        case Constants.ACTION_INCOMING_CALL:
        case Constants.ACTION_ACCEPT:
          launchService(serviceIntent);
          launchMainActivity(appIntent);
          break;
        default:
          launchService(serviceIntent);
          break;
      }
    }
  }

  private void launchMainActivity(Intent intent) {
    try{
      Intent launchIntent = new Intent(intent);
      launchIntent.setClass(this, VoiceActivity.class);
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(launchIntent);
    }catch (Exception e){
      e.printStackTrace();
    }
  }
  private void launchService(Intent intent) {
    Intent launchIntent = new Intent(intent);
    launchIntent.setClass(this, IncomingCallNotificationService.class);
    startService(launchIntent);
  }
}
