## Twilio Voice Quickstart for Android

> NOTE: This sample application uses the Programmable Voice Android 6.x APIs. If you are using prior versions of the SDK, we highly recommend planning your migration to the latest version as soon as possible.

## Get started with Voice on Android

- [Quickstart](#quickstart) - Run the quickstart app
- [Examples](#examples) - Customize your voice experience with these examples

## References
- [Access Tokens](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/access-token.md) - Using access tokens
- [Managing Push Credentials](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/manage-push-credentials.md) - Managing Push Credentials
- [Managing Regional Push Credentials using Notify Credential Resource API](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/push-credentials-via-notify-api.md) - Create or update push credentials for regional usage
- [Troubleshooting](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/troubleshooting.md) - Troubleshooting
- [More Documentation](#more-documentation) - More documentation related to the Voice Android SDK
- [Emulator Support](#emulator-support) - Android emulator support
- [Reducing APK Size](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/reducing-apk-size.md) - Use ABI splits to reduce APK size
- [Twilio Helper Libraries](#twilio-helper-libraries) - TwiML quickstarts.
- [Issues & Support](#issues-and-support) - Filing issues and general support

## Voice Android SDK Versions
- [Migration Guide 4.x to 5.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-4.x-5.x.md) - Migrating from 4.x to 5.x
- [New Features 4.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-4.0.md) - New features in 4.0
- [Migration Guide 3.x to 4.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-3.x-4.x.md) - Migrating from 3.x to 4.x
- [New Features 3.0](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/new-features-3.0.md) - New features in 3.0
- [Migration Guide 2.x to 3.x](https://github.com/twilio/voice-quickstart-android/blob/master/Docs/migration-guide-2.x-3.x.md) - Migrating from 2.x to 3.x

## Quickstart

The quickstart is broken into two flavors, "standard" & "connection_service", the latter showing how to integrate with the Android Telecom subsystem but requiring Android API 26.  To get started with the Quickstart application follow these steps. Steps 1-5 will enable the application to make a call. The remaining steps 7-10 will enable the application to receive incoming calls in the form of push notifications using FCM.

1. [Generate google-services.json](#bullet1)
2. [Open this project in Android Studio](#bullet2)
3. [Use Twilio CLI to deploy access token and TwiML application to Twilio Serverless](#bullet3)
4. [Create a TwiML application for the access token](#bullet4)
5. [Generate an access token for the quickstart](#bullet5)
6. [Run the app](#bullet6)
7. [Create a Push Credential using your FCM Server API Key](#bullet7)
8. [Receive an incoming call](#bullet8)
9. [Make client to client call](#bullet9)
10. [Make client to PSTN call](#bullet10)


### <a name="bullet1"></a>1. Generate `google-services.json`

The Programmable Voice Android SDK uses Firebase Cloud Messaging push notifications to let your application know when it is receiving an incoming call. If you want your users to receive incoming calls, you’ll need to enable FCM in your application.

Follow the steps under **Use the Firebase Assistant** in the [Firebase Developers Guide](https://firebase.google.com/docs/android/setup). Once you connect and sync to Firebase successfully, you will be able to download the `google-services.json` for your application. 

Login to Firebase console and make a note of generated `Server Key`. You will need them in [step 7](#bullet7).

Make sure the generated `google-services.json` is downloaded to the `app` directory of the quickstart project to replace the existing `app/google-services.json` stub json file. If you are using the Firebase plugin make sure to remove the stub `google-services.json` file first.

Missing valid `google-services.json` will result in a build failure with the following error message :
<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/invalid_google_service_json_error.png">"

### <a name="bullet2"></a>2. Open the project in Android Studio

<img width="700px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/import_project.png"/>

### <a name="bullet3"></a>3. Use Twilio CLI to deploy access token and TwiML application to Twilio Serverless

You must have the following installed:

* [Node.js v10+](https://nodejs.org/en/download/)
* NPM v6+ (comes installed with newer Node versions)

Run `npm install` to install all dependencies from NPM.

Install [twilio-cli](https://www.twilio.com/docs/twilio-cli/quickstart) with:

    $ npm install -g twilio-cli

Login to the Twilio CLI. You will be prompted for your Account SID and Auth Token, both of which you can find on the dashboard of your [Twilio console](https://twilio.com/console).

    $ twilio login

Once successfully logged in, an API Key, a secret get created and stored in your keychain as the twilio-cli password in `SKxxxx|secret` format. Please make a note of these values to use them in the `Server/.env` file.	
 
<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/twilio_cli_key_chain_access.png">

This app requires the [Serverless plug-in](https://github.com/twilio-labs/plugin-serverless). Install the CLI plugin with:

    $ twilio plugins:install @twilio-labs/plugin-serverless

Before deploying, create a `Server/.env` by copying from `Server/.env.example`

    $ cp Server/.env.example Server/.env

Update `Server/.env` with your Account SID, auth token, API Key and secret.
    
    ACCOUNT_SID=ACxxxx
    AUTH_TOKEN=xxxxxx
    API_KEY_SID=SKxxxx
    API_SECRET=xxxxxx
    APP_SID=APxxxx(available in step 4)
    PUSH_CREDENTIAL_SID=CRxxxx(available in step 7)

The `Server` folder contains a basic server component which can be used to vend access tokens or generate TwiML response for making call to a number or another client. The app is deployed to Twilio Serverless with the `serverless` plug-in:

    $ cd Server
    $ twilio serverless:deploy

The server component that's baked into this quickstart is in Node.js. If you’d like to roll your own or better understand the Twilio Voice server side implementations, please see the list of starter projects in the following supported languages below:

* [voice-quickstart-server-java](https://github.com/twilio/voice-quickstart-server-java)
* [voice-quickstart-server-node](https://github.com/twilio/voice-quickstart-server-node)
* [voice-quickstart-server-php](https://github.com/twilio/voice-quickstart-server-php)
* [voice-quickstart-server-python](https://github.com/twilio/voice-quickstart-server-python)

Follow the instructions in the project's README to get the application server up and running locally and accessible via the public Internet.

### <a name="bullet4"></a>4. Create a TwiML application for the Access Token

Next, we need to create a TwiML application. A TwiML application identifies a public URL for retrieving [TwiML call control instructions](https://www.twilio.com/docs/api/twiml). When your QS app makes a call to the Twilio cloud, Twilio will make a webhook request to this URL, your application server will respond with generated TwiML, and Twilio will execute the instructions you’ve provided.

Use Twilio CLI to create a TwiML app with the `make-call` endpoint you have just deployed

    $ twilio api:core:applications:create \
        --friendly-name=my-twiml-app \
        --voice-method=POST \
        --voice-url="https://my-quickstart-dev.twil.io/make-call"

You should receive an Appliciation SID that looks like this

    APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

### <a name="bullet5"></a>5. Generate an access token for the quickstart

Install the `token` plug-in

    $ twilio plugins:install @twilio-labs/plugin-token

Use the TwiML App SID you just created to generate an access token

    $ twilio token:voice --identity=alice --voice-app-sid=APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

Copy the access token string. Your Android app will use this token to connect to Twilio.


### <a name="bullet6"></a>6. Run the app

Now let’s go back to the `app`, update the placeholder of `accessToken` with access token string you just copied in `VoiceActivity.java`.

```
 private String accessToken = "PASTE_YOUR_ACCESS_TOKEN_HERE";
```

Build and run the quickstart app on an Android device.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_activity.png">

Press the call button to open the call dialog.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Leave the dialog text field empty and press the call button to start a call. You will hear the congratulatory message. Support for dialing another client or number is described in steps 9 and 10.

<img width="423px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call.png">


### <a name="bullet7"></a>7. Create a Push Credential using your FCM Server Key

You will need to store the FCM Server key(The **Server key** of your project from the Firebase console, found under Settings/Cloud messaging) with Twilio so that we can send push notifications to your app on your behalf. Once you store the Server key with Twilio, it will get assigned a Push Credential SID so that you can later specify which key we should use to send push notifications.

Use Twilio CLI to create a Push Credential using the FCM Server Key. 

    $ twilio api:chat:v2:credentials:create \
        --type=fcm \
        --friendly-name="voice-push-credential-fcm" \
        --secret=SERVER_KEY_VALUE

This will return a Push Credential SID that looks like this

    CRxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

Now let's generate another access token and add the Push Credential to the Voice Grant.

    $ twilio token:voice \
        --identity=alice \
        --voice-app-sid=APxxxx \
        --push-credential-sid=CRxxxxs


### <a name="bullet8"></a>8. Receiving an Incoming Notification

You are now ready to receive incoming calls. Update your app with the access token generated from step 7 and rebuild your app. The `Voice.register()` method will register your mobile application with the FCM device token as well as the access token. Once registered, hit your application server's **/place-call** endpoint: `https://my-quickstart-dev.twil.io/place-call?to=alice`.  This will trigger a Twilio REST API request that will make an inbound call to your mobile app.

Your application will be brought to the foreground and you will see an alert dialog. The app will be brought to foreground even when your screen is locked.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/incoming_call.png">"

Once your app accepts the call, you should hear a congratulatory message.

### <a name="bullet9"></a>9. Make client to client call

To make client to client calls, you need the application running on two devices. To run the application on an additional device, make sure you use a different identity in your access token when registering the new device. 

Press the call button to open the call dialog.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Enter the client identity of the newly registered device to initiate a client to client call from the first device.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/make_call_to_client.png">
<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/incoming_call_from_alice.png">

### <a name="bullet10"></a>10. Make client to PSTN call

A verified phone number is one that you can use as your Caller ID when making outbound calls with Twilio. This number has not been ported into Twilio and you do not pay Twilio for this phone number.

To make client to number calls, first get a valid Twilio number to your account via https://www.twilio.com/console/phone-numbers/verified. Update your server code and replace the `callerNumber` with the verified number. Restart the server so that it uses the new value.

Press the call button to open the call dialog.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/voice_make_call_dialog.png">

Enter a PSTN number and press the call button to place a call.

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/make_call_to_number.png">

## Examples
In addition to the quickstart we've also added an example that shows how to create and customize media experience in your app:

- [Custom Audio Device](https://github.com/twilio/voice-quickstart-android/tree/master/exampleCustomAudioDevice) - Demonstrates how to use Twilio's Programmable Voice SDK with audio playback and recording functionality provided by a custom `AudioDevice`. 

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
