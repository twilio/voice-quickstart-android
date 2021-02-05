# Twilio Voice Quickstart for Android

> NOTE: This sample application uses the Programmable Voice Android 5.x APIs. If you are using prior versions of the SDK, we highly recommend planning your migration to 5.0 as soon as possible.

## Get started with Voice on Android

- [Quickstart](#quickstart) - Run the quickstart app
- [Examples](#examples) - Customize your voice experience with these examples

## Voice Android SDK Versions
- [Migration Guide 4.x to 5.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-4.x-5.x.md) - Migrating from 4.x to 5.x
- [New Features 4.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-4.0.md) - New features in 4.0
- [Migration Guide 3.x to 4.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-3.x-4.x.md) - Migrating from 3.x to 4.x
- [New Features 3.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-3.0.md) - New features in 3.0
- [Migration Guide 2.x to 3.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-2.x-3.x.md) - Migrating from 2.x to 3.x

## References
- [Access Tokens](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/access-token.md) - Using access tokens
- [Managing Push Credentials](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/manage-push-credentials.md) - Managing Push Credentials
- [Troubleshooting Audio](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/troubleshooting-audio.md) - Troubleshooting Audio
- [More Documentation](#more-documentation) - More documentation related to the Voice Android SDK
- [Emulator Support](#emulator-support) - Android emulator support
- [Reducing APK Size](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/reducing-apk-size.md) - Use ABI splits to reduce APK size
- [Twilio Helper Libraries](#twilio-helper-libraries) - TwiML quickstarts.
- [Issues & Support](#issues-and-support) - Filing issues and general support

## Quickstart

To get started with the Quickstart application follow these steps. Steps 1-6 will allow you to make a call. The remaining steps 7-9 will enable push notifications using FCM.

1. [Generate google-services.json](#bullet1)
2. [Open this project in Android Studio](#bullet2)
3. [Create a Voice API key](#bullet3)
4. [Configure a server to generate an access token to use in the app](#bullet4)
5. [Create a TwiML application](#bullet5)
6. [Configure your application server](#bullet6)
7. [Run the app](#bullet7)
8. [Add a Push Credential using your FCM Server API Key](#bullet8)
9. [Receiving an Incoming Notification](#bullet9)
10. [Make client to client call](#bullet10)
11. [Make client to PSTN call](#bullet11)

### <a name="bullet1"></a>1. Generate `google-services.json`

The Programmable Voice Android SDK uses Firebase Cloud Messaging push notifications to let your application know when it is receiving an incoming call. If you want your users to receive incoming calls, you’ll need to enable FCM in your application.

Follow the steps under **Use the Firebase Assistant** in the [Firebase Developers Guide](https://firebase.google.com/docs/android/setup). Once you connect and sync to Firebase successfully, you will be able to download the `google-services.json` for your application. 

Login to Firebase console and make a note of generated `Server API Key` and `Sender ID` in your notepad. You will need them in [step 8](#bullet8).

Make sure the generated `google-services.json` is downloaded to the `app` directory of the quickstart project to replace the existing `app/google-services.json` stub json file. If you are using the Firebase plugin make sure to remove the stub `google-services.json` file first.

Missing valid `google-services.json` will result in a build failure with the following error message :
<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/invalid_google_service_json_error.png">"

### <a name="bullet2"></a>2. Open the project in Android Studio
<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/import_project.png"/>

### <a name="bullet3"></a>3. Create a Voice API key

Go to the [API Keys page](https://www.twilio.com/console/voice/settings/api-keys) and create a new API key.

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/add_api_key.png"/>

Save the generated `API_KEY` and `API_KEY_SECRET` in your notepad. You will need them in the next step.


### <a name="bullet4"></a>4. Configure a server to generate an access token to use in the app

Download one of the starter projects for the server.

* [voice-quickstart-server-java](https://github.com/twilio/voice-quickstart-server-java)
* [voice-quickstart-server-node](https://github.com/twilio/voice-quickstart-server-node)
* [voice-quickstart-server-php](https://github.com/twilio/voice-quickstart-server-php)
* [voice-quickstart-server-python](https://github.com/twilio/voice-quickstart-server-python)

Follow the instructions in the server's README to get the application server up and running locally and accessible via the public Internet. For now just add the Twilio Account SID that you can obtain from the console, and  the `API_KEY` and `API_SECRET` you obtained in the previous step. For example:

    ACCOUNT_SID=AC12345678901234567890123456789012
    API_KEY=SK12345678901234567890123456789012
    API_KEY_SECRET=the_secret_generated_when_creating_the_api_key

### <a name="bullet5"></a>5. Create a TwiML application

Next, we need to create a TwiML application. A TwiML application identifies a public URL for retrieving TwiML call control instructions. When your Android app makes a call to the Twilio cloud, Twilio will make a webhook request to this URL, your application server will respond with generated TwiML, and Twilio will execute the instructions you’ve provided.

To create a TwiML application, go to the TwiML app page. Create a new TwiML application, and use the public URL of your application server’s `/makeCall` endpoint as the Voice Request URL (If your app server is written in PHP, then you need `.php` extension at the end).

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/create_twml_app.png"/>

As you can see we’ve used our ngrok public address in the Request URL field above.

Save your TwiML Application configuration, and grab the TwiML Application SID (a long identifier beginning with the characters "AP").

### <a name="bullet6"></a>6. Configure your application server

Put the remaining `APP_SID` configuration info into your application server by setting the following constants with the information you gathered above. For example:

    ACCOUNT_SID=AC12345678901234567890123456789012
    API_KEY=SK12345678901234567890123456789012
    API_KEY_SECRET=the_secret_generated_when_creating_the_api_key
    APP_SID=AP12345678901234567890123456789012

Once you’ve done that, restart the server so it uses the new configuration info. Now it's time to test.

Open up a browser and visit the URL for your application server's Access Token endpoint: `https://{YOUR_SERVER_URL}/accessToken` (If your app server is written in PHP, then you need `.php` extension at the end). If everything is configured correctly, you should see a long string of letters and numbers, which is a Twilio Access Token. Your Android app will use a token like this to connect to Twilio.

### <a name="bullet7"></a>7. Run the app

Paste the public URL of your application server’s `https://{YOUR_SERVER_URL}/accessToken` endpoint into `TWILIO_ACCESS_TOKEN_SERVER_URL` in VoiceActivity.java. Make sure to include `/accessToken` in the URL path.

<img width="600px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/paste_token_server_url.png"/>

Run the quickstart app on an Android device

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_activity.png">

Press the call button to open the call dialog.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Leave the dialog text field empty and press the call button to start a call. You will hear the congratulatory message. Support for dialing another client or number is described in steps 10 and 11.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call.png">


### <a name="bullet8"></a>8. Add a Push Credential using your FCM `Server API Key` 

You will need to store the FCM `Server API Key` with Twilio so that we can send push notifications to your app on your behalf. Once you store the API Key with Twilio, it will get assigned a Push Credential SID so that you can later specify which key we should use to send push notifications.

Go to the [Push Credentials page](https://www.twilio.com/console/voice/sdks/credentials) and create a new Push Credential.

Paste in the `Server API Key` saved in [step 2](#bullet2) and press Save.

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

## Examples
In addition to the quickstart we've also added an example that shows how to create and customize media experience in your app:

- [Custom Audio Device](exampleCustomAudioDevice) - Demonstrates how to use Twilio's Programmable Voice SDK with audio playback and recording functionality provided by a custom `AudioDevice`. 

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
