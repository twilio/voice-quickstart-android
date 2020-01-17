> NOTE: This sample application uses the Programmable Voice Android 5.x APIs. If you are using prior versions of the SDK, we highly recommend planning your migration to 5.0 as soon as possible.

# Twilio Voice Quickstart for Android

Get started with Voice on Android:

- [Quickstart](#quickstart) - Run the quickstart app
- [Migration Guide 4.x to 5.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-4.x-5.x.md) - Migrating from 4.x to 5.x
- [New Features 4.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-4.0.md) - New features in 4.0
- [Migration Guide 3.x to 4.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-3.x-4.x.md) - Migrating from 3.x to 4.x
- [New Features 3.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-3.0.md) - New features in 3.0
- [Migration Guide 2.x to 3.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-2.x-3.x.md) - Migrating from 2.x to 3.x
- [Emulator Support](#emulator-support) - Android emulator support
- [Reducing APK Size](#reducing-apk-size) - Use ABI splits to reduce APK size
- [Access Tokens](#access-tokens) - Using access tokens
- [Managing Push Credentials](#managing-push-credentials) - Managing Push Credentials
- [Troubleshooting Audio](#troubleshooting-audio) - Troubleshooting Audio
- [More Documentation](#more-documentation) - More documentation related to the Voice Android SDK
- [Twilio Helper Libraries](#twilio-helper-libraries) - TwiML quickstarts.
- [Issues & Support](#issues-and-support) - Filing issues and general support

## Quickstart

To get started with the Quickstart application follow these steps. Steps 1-6 will allow you to make a call. The remaining steps 7-8 will enable push notifications using FCM.

1. [Open this project in Android Studio](#bullet1)
2. [Create a Voice API key](#bullet2)
3. [Configure a server to generate an access token to use in the app](#bullet3)
4. [Create a TwiML application](#bullet4)
5. [Configure your application server](#bullet5)
6. [Run the app](#bullet6)
7. [Generate google-services.json](#bullet7)
8. [Add a Push Credential using your FCM Server API Key](#bullet8)
9. [Receiving an Incoming Notification](#bullet9)
10. [Make client to client call](#bullet10)
11. [Make client to PSTN call](#bullet11)


### <a name="bullet1"></a>1. Open the project in Android Studio
<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/import_project.png"/>

### <a name="bullet2"></a>2. Create a Voice API key

Go to the [API Keys page](https://www.twilio.com/console/voice/settings/api-keys) and create a new API key.

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/add_api_key.png"/>

Save the generated `API_KEY` and `API_KEY_SECRET` in your notepad. You will need them in the next step.


### <a name="bullet3"></a>3. Configure a server to generate an access token to use in the app

Download one of the starter projects for the server.

* [voice-quickstart-server-java](https://github.com/twilio/voice-quickstart-server-java)
* [voice-quickstart-server-node](https://github.com/twilio/voice-quickstart-server-node)
* [voice-quickstart-server-php](https://github.com/twilio/voice-quickstart-server-php)
* [voice-quickstart-server-python](https://github.com/twilio/voice-quickstart-server-python)

Follow the instructions in the server's README to get the application server up and running locally and accessible via the public Internet. For now just add the Twilio Account SID that you can obtain from the console, and  the `API_KEY` and `API_SECRET` you obtained in the previous step. For example:

    ACCOUNT_SID=AC12345678901234567890123456789012
    API_KEY=SK12345678901234567890123456789012
    API_KEY_SECRET=the_secret_generated_when_creating_the_api_key

### <a name="bullet4"></a>4. Create a TwiML application

Next, we need to create a TwiML application. A TwiML application identifies a public URL for retrieving TwiML call control instructions. When your Android app makes a call to the Twilio cloud, Twilio will make a webhook request to this URL, your application server will respond with generated TwiML, and Twilio will execute the instructions you’ve provided.

To create a TwiML application, go to the TwiML app page. Create a new TwiML application, and use the public URL of your application server’s `/makeCall` endpoint as the Voice Request URL (If your app server is written in PHP, then you need `.php` extension at the end).

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/create_twml_app.png"/>

As you can see we’ve used our ngrok public address in the Request URL field above.

Save your TwiML Application configuration, and grab the TwiML Application SID (a long identifier beginning with the characters "AP").

### <a name="bullet5"></a>5. Configure your application server

Put the remaining `APP_SID` configuration info into your application server by setting the following constants with the information you gathered above. For example:

    ACCOUNT_SID=AC12345678901234567890123456789012
    API_KEY=SK12345678901234567890123456789012
    API_KEY_SECRET=the_secret_generated_when_creating_the_api_key
    APP_SID=AP12345678901234567890123456789012

Once you’ve done that, restart the server so it uses the new configuration info. Now it's time to test.

Open up a browser and visit the URL for your application server's Access Token endpoint: `https://{YOUR_SERVER_URL}/accessToken` (If your app server is written in PHP, then you need `.php` extension at the end). If everything is configured correctly, you should see a long string of letters and numbers, which is a Twilio Access Token. Your Android app will use a token like this to connect to Twilio.

### <a name="bullet6"></a>6. Run the app

Paste the public URL of your application server’s `https://{YOUR_SERVER_URL}/accessToken` endpoint into `TWILIO_ACCESS_TOKEN_SERVER_URL` in VoiceActivity.java. Make sure to include `/accessToken` in the URL path.

<img width="600px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/paste_token_server_url.png"/>

Run the quickstart app on an Android device

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_activity.png">

Press the call button to open the call dialog.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Leave the dialog text field empty and press the call button to start a call. You will hear the congratulatory message. Support for dialing another client or number is described in steps 10 and 11.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call.png">

### <a name="bullet7"></a>7. Generate `google-services.json`

The Programmable Voice Android SDK uses Firebase Cloud Messaging push notifications to let your application know when it is receiving an incoming call. If you want your users to receive incoming calls, you’ll need to enable FCM in your application.

Follow the steps under **Use the Firebase Assistant** in the [Firebase Developers Guide](https://firebase.google.com/docs/android/setup). Once you connect and sync to Firebase successfully, you will be able to download the `google-services.json` for your application. 

Login to Firebase console and make a note of generated `Server API Key` and `Sender ID` in your notepad. You will need them in the next step.

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/server_key_sender_id.png">"

Make sure the generated `google-services.json` is downloaded to the `app` directory of the quickstart project to replace the existing `app/google-services.json` stub json file. If you are using the Firebase plugin make sure to remove the stub `google-services.json` file first.

As a final step re-run the application from Android Studio to ensure the APK now has the latest `google-services.json` file.

### <a name="bullet8"></a>8. Add a Push Credential using your FCM `Server API Key` 

You will need to store the FCM `Server API Key` with Twilio so that we can send push notifications to your app on your behalf. Once you store the API Key with Twilio, it will get assigned a Push Credential SID so that you can later specify which key we should use to send push notifications.

Go to the [Push Credentials page](https://www.twilio.com/console/voice/sdks/credentials) and create a new Push Credential.

Paste in the `Server API Key` and press Save.

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/add_fcm_push_cred.png">"

### <a name="bullet9"></a>9. Receiving an Incoming Notification

Put the `PUSH_CREDENTIAL_SID` configuration info into your application server by setting the following constants with the information you gathered above. For example:

    ACCOUNT_SID=AC12345678901234567890123456789012
    API_KEY=SK12345678901234567890123456789012
    API_KEY_SECRET=the_secret_generated_when_creating_the_api_key
    PUSH_CREDENTIAL_SID=CR12345678901234567890123456789012
    APP_SID=AP12345678901234567890123456789012

Once you’ve done that, restart the server so it uses the new configuration info. Now it's time to test. Use your browser to initiate an incoming call by navigating to the public URL of your application server’s `https://{YOUR_SERVER_URL}/placeCall` endpoint (If your app server is written in PHP, then you need `.php` extension at the end). This will trigger a Twilio REST API request that will make an inbound call to your mobile app.
Your application will be brought to the foreground and you will see an alert dialog. The app will be brought to foreground even when your screen is locked.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/incoming_call.png">"

You will receive an incoming call notification as well. If you pull down the notification drawer, you will be able to view the notification.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/incoming_notification.png">"

Once your app accepts the call, you should hear a congratulatory message.

### <a name="bullet10"></a>10. Make client to client call

To make client to client calls, you need the application running on two devices. To run the application on an additional device, make sure you use a different identity in your access token when registering the new device. For example, change the `identity` field to `bob` and run the application. 

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/access_token_identity_bob.png">

Press the call button to open the call dialog.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Enter the client identity of the newly registered device to initiate a client to client call from the first device.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/make_call_to_client.png">
<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/incoming_call_from_alice.png">

### <a name="bullet11"></a>11. Make client to PSTN call

A verified phone number is one that you can use as your Caller ID when making outbound calls with Twilio. This number has not been ported into Twilio and you do not pay Twilio for this phone number.

To make client to number calls, first get a valid Twilio number to your account via https://www.twilio.com/console/phone-numbers/verified. Update your server code and replace the caller number variable  (`CALLER_NUMBER` or `callerNumber` depending on which server you chose) with the verified number. Restart the server so that it uses the new value.

Press the call button to open the call dialog.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Enter a PSTN number and press the call button to place a call.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/make_call_to_number.png">

## Emulator Support

The SDK supports using emulators except in the following known cases:

1. Emulators with API 22 or lower have bad audio emulation, the sound is generally inaudible
2. Emulators must have Google Play services support to use FCM to receive call invites
3. Running on x86 API 25 emulators results in application crashes

In general we advise using a real device when doing development with our SDK since real-time audio is a performance oriented operation.

## Reducing APK Size

Our library is built using native libraries. As a result, if you use the default gradle build you will generate an APK with all four architectures(armeabi-v7a, arm64-v8a, x86, x86_64) in your APK.

[APK splits](https://developer.android.com/studio/build/configure-apk-splits.html) allow developers to build multiple APKs for different screen sizes and ABIs. Enabling APK splits ensures that the minimum amount of files required to support a particular device are packaged into an APK.

The following snippet shows an example `build.gradle` with APK splits enabled.

    apply plugin: 'com.android.application'

    android {
        // Specify that we want to split up the APK based on ABI
        splits {
            abi {
                // Enable ABI split
                enable true

                // Clear list of ABIs
                reset()

                // Specify each architecture currently supported by the Voice SDK
                include "armeabi-v7a", "arm64-v8a", "x86", "x86_64"

                // Specify that we do not want an additional universal SDK
                universalApk false
            }
        }
    }

The adoption of APK splits requires developers to submit multiple APKs to the Play Store. Refer to [Google’s documentation](https://developer.android.com/google/play/publishing/multiple-apks.html)  for how to support this in your application.

## Access Tokens

The access token generated by your server component is a [jwt](https://jwt.io) that contains a `grant` for Programmable Voice, an `identity` that you specify, and a `time-to-live` that sets the lifetime of the generated access token. The default `time-to-live` is 1 hour and is configurable up to 24 hours using the Twilio helper libraries.

### Uses

In the Android SDK the access token is used for the following:

1. To make an outgoing call via `Voice.call(Context context, String accessToken, String twiMLParams, Call.Listener listener)`
2. To register or unregister for incoming notifications via GCM or FCM via `Voice.register(String accessToken, Voice.RegistrationChannel registrationChannel, String registrationToken, RegistrationListener listener)` and `Voice.unregister(String accessToken, Voice.RegistrationChannel registrationChannel, String registrationToken, RegistrationListener listener)`. Once registered, incoming notifications are handled via a `CallInvite` where you can choose to accept or reject the invite. When accepting the call an access token is not required. Internally the `CallInvite` has its own accessToken that ensures it can connect to our infrastructure.

### Managing Expiry

As mentioned above, an access token will eventually expire. If an access token has expired, our infrastructure will return error `EXCEPTION_INVALID_ACCESS_TOKEN_EXPIRY`/`20104` via a `CallException` or a `RegistrationException`.

There are number of techniques you can use to ensure that access token expiry is managed accordingly:

- Always fetch a new access token from your access token server before making an outbound call.
- Retain the access token until getting a `EXCEPTION_INVALID_ACCESS_TOKEN_EXPIRY`/`20104` error before fetching a new access token.
- Retain the access token along with the timestamp of when it was requested so you can verify ahead of time whether the token has already expired based on the `time-to-live` being used by your server.
- Prefetch the access token whenever the `Application`, `Service`, `Activity`, or `Fragment` associated with an outgoing call is created.

## Playing Custom Ringtone 

When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge) is enabled in the `<Dial>` TwiML verb, the caller will not hear the ringback while the call is ringing and awaiting to be accepted on the callee's side. The application can use the `SoundPoolManager` to play custom audio files between the `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks. To enable this behavior, add `playCustomRingback` as an environment variable or a property in `local.properties` file and set it to `true`.

```
playCustomRingback=true
```

## Managing Push Credentials

A Push Credential is a record for a push notification channel, for Android this Push Credential is a push notification channel record for FCM or GCM. Push Credentials are managed in the console under [Mobile Push Credentials](https://www.twilio.com/console/voice/sdks/credentials).

Whenever a registration is performed via `Voice.register(…)` in the Android SDK the `identity` and the `Push Credential SID` provided in the JWT based access token, along with the `FCM/GCM token` are used as a unique address to send push notifications to this application instance whenever a call is made to reach that `identity`. Using `Voice.unregister(…)` removes the association for that `identity`.

### Updating a Push Credential

If you need to change or update your server key token provided by Firebase (under `Project Settings` → `Cloud Messaging` → `Server key`) you can do so by selecting the Push Credential in the [console](https://www.twilio.com/console/voice/sdks/credentials) and adding your new `Server key` in the text box provided on the Push Credential page shown below:

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/updating_push_credential.png">

### Deleting a Push Credential

We **do not recommend that you delete a Push Credential** unless the application that it was created for is no longer being used.

When a Push Credential is deleted **any associated registrations made with this Push Credential will be deleted**. Future attempts to reach an `identity` that was registered using the Push Credential SID of this deleted push credential will fail.

If you are certain you want to delete a Push Credential you can click on `Delete this Credential` on the [console](https://www.twilio.com/console/voice/sdks/credentials) page of the selected Push Credential.

Please ensure that after deleting the Push Credential you remove or replace the Push Credential SID when generating new access tokens.

## Troubleshooting Audio
The following sections provide guidance on how to ensure optimal audio quality in your applications.

### Configuring AudioManager

The `AudioManager` configuration guidance below is meant to provide optimal audio experience when in a `Call`. This configuration is inspired by the [WebRTC Android example](https://chromium.googlesource.com/external/webrtc/+/refs/heads/master/examples/androidapp/src/org/appspot/apprtc/AppRTCAudioManager.java) and the [Android documentation](https://developer.android.com/reference/android/media/AudioFocusRequest).

```
 private void setAudioFocus(boolean setFocus) {
    if (audioManager != null) {
        if (setFocus) {
            savedAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int i) {
                            }
                        })
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {

		                                       @Override
		                                       public void onAudioFocusChange(int focusChange) {
		                                       }
		                                   }, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
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
```

### Configuring Hardware Audio Effects
Our library performs acoustic echo cancellation (AEC), noise suppression (NS), and auto gain
control (AGC) using device hardware by default. Using device hardware is more efficient, but some
devices do not implement these audio effects well. If you are experiencing echo, background noise,
or unexpected volume levels on certain devices reference the following snippet for enabling
software implementations of AEC, NS, and AGC.

    /*
     * Execute any time before invoking `Voice.connect(...)` or `CallInvite.accept(...)`.
     */

    // Use software AEC
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);

    // Use sofware NS
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

    // Use software AGC
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);

### Configuring OpenSL ES

Starting with Voice SDK 4.3.0, our library does not use [OpenSL ES](https://developer.android.com/ndk/guides/audio/opensl/index.html)
for audio playback by default. Prior versions starting with Voice SDK 3.0.0 did use OpenSL ES by default. Using OpenSL ES is more efficient, but can cause
problems with other audio effects. For example, we found on the Nexus 6P that OpenSL ES affected
the device's hardware echo canceller so we blacklisted the Nexus 6P from using OpenSL ES. If you
are experiencing audio problems with a device that cannot be resolved using software audio effects,
reference the following snippet for enabling OpenSL ES:

    /*
     * Execute any time before invoking `Voice.connect(...)` or `CallInvite.accept(...)`.
     */

    // Enable OpenSL ES
    tvo.webrtc.voiceengine.WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);

    // Check if OpenSL ES is disabled
    tvo.webrtc.voiceengine.WebRtcAudioUtils.deviceIsBlacklistedForOpenSLESUsage();

### Managing Device Specific Configurations
The Voice Android SDK does not maintain a list of devices for which hardware effects or OpenSL ES are disabled. We recommend maintaining a list in your own application and disabling these effects as needed. The [Signal App provides a great example](https://github.com/signalapp/Signal-Android/blob/master/src/org/thoughtcrime/securesms/ApplicationContext.java#L250) of how to maintain a list and disable the effects as needed.

### Handling Low Headset Volume
If your application experiences low playback volume, we recommend the following snippets: 

#### Android N and Below
```
int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
    
                                   @Override
                                   public void onAudioFocusChange(int focusChange) {
                                   }
                               }, AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
```
                     

#### Android O and Up :
```
AudioAttributes playbackAttributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build();
AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(playbackAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int i) {
            }
        })
        .build();
```    

## More Documentation

You can find more documentation on getting started as well as our latest Javadoc below:


* [Getting Started](https://www.twilio.com/docs/api/voice-sdk/android/getting-started)
* [Javadoc](https://media.twiliocdn.com/sdk/android/voice/latest/docs/)

## Twilio Helper Libraries

To learn more about how to use TwiML and the Programmable Voice Calls API, check out our TwiML quickstarts:

* [TwiML Quickstart for Python](https://www.twilio.com/docs/quickstart/python/twiml)
* [TwiML Quickstart for Ruby](https://www.twilio.com/docs/quickstart/ruby/twiml)
* [TwiML Quickstart for PHP](https://www.twilio.com/docs/quickstart/php/twiml)
* [TwiML Quickstart for Java](https://www.twilio.com/docs/quickstart/java/twiml)
* [TwiML Quickstart for C#](https://www.twilio.com/docs/quickstart/csharp/twiml)

## Issues and Support

Please file any issues you find here on Github.
For general inquiries related to the Voice SDK you can file a support ticket.
Please ensure that you are not sharing any
[Personally Identifiable Information(PII)](https://www.twilio.com/docs/glossary/what-is-personally-identifiable-information-pii)
or sensitive account information (API keys, credentials, etc.) when reporting an issue.

## License
MIT
