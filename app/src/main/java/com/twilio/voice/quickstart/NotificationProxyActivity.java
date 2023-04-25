package com.twilio.voice.quickstart;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

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
    String action = intent.getAction();
    if (action != null) {
      switch (action) {
        case Constants.ACTION_INCOMING_CALL:
        case Constants.ACTION_ACCEPT:
          launchService(intent);
          launchMainActivity(intent);
          break;
        default:
          launchService(intent);
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
