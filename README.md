# Twilio Voice Quickstart for Android

Get started with Voice on Android:

- [Quickstart](#quickstart) - Run the quickstart app
- [Reducing APK Size](#reducing-apk-size) - Use ABI splits to reduce APK size
- [More Documentation](#more-documentation) - More documentation related to the Video Android SDK
- [Issues & Support](#issues-and-support) - Filing issues and general support

## Quickstart

To get started with the Quickstart application follow these steps. Steps 1-6 will allow you to make a call. The remaining steps 7-8 will enable push notifications using GCM.


1. [Open this project in Android Studio](#bullet1)
2. [Create a Voice API key] (#bullet2)
3. [Configure a server to generate an access token to use in the app](#bullet3)
4. [Create a TwiML application](#bullet4)
5. [Configure your application server](#bullet5)
6. [Run the app](#bullet6)
7. [Generate google-services.json from the Google Developer Console](#bullet7)
8. [Add a Push Credential using your GCM Server API Key](#bullet8)
9. [Receiving an Incoming Notification](#bullet9)


### <a name="bullet1"></a>1. Open the project in Android Studio
<img width="700px" src="images/quickstart/import_project.png"/>

For now you will see an error in the console stating `Failed to retrieve GCM token`  and this is because you don’t have a valid `google-services.json` file yet to enable push notifications. We will obtain it in step 7.

<img width="700px" src="images/quickstart/invalid_sender.png"/>

### <a name="bullet2"></a>2. Create a Voice API key

Go to the [API Keys page](https://www.twilio.com/console/voice/dev-tools/api-keys) and create a new API key.

<img width="700px" src="images/quickstart/add_api_key.png"/>

Save the generated `API_KEY` and `API_KEY_SECRET` in your notepad. You will need them in the next step.


### <a name="bullet3"></a>3. Configure a server to generate an access token to use in the app

[Download starter project for the server.](https://github.com/twilio/voice-quickstart-server-python)
Follow the instructions in the server's README to get the application server up and running locally and accessible via the public Internet. For now just add the Twilio Account SID that you can obtain from the console, and  the `API_KEY` and `API_SECRET` you obtained in the previous step. 

    ACCOUNT_SID = 'AC***'
    API_KEY = 'SK***'
    API_KEY_SECRET = '***'


### <a name="bullet4"></a>4. Create a TwiML application

Next, we need to create a TwiML application. A TwiML application identifies a public URL for retrieving TwiML call control instructions. When your Android app makes a call to the Twilio cloud, Twilio will make a webhook request to this URL, your application server will respond with generated TwiML, and Twilio will execute the instructions you’ve provided.

To create a TwiML application, go to the TwiML app page. Create a new TwiML application, and use the public URL of your application server’s `/outgoing` endpoint as the Voice Request URL.

<img width="700px" src="images/quickstart/create_twml_app.png"/>


As you can see we’ve used our ngrok public address in the Request URL field above.

Save your TwiML Application configuration, and grab the TwiML Application SID (a long identifier beginning with the characters "AP").


### <a name="bullet5"></a>5. Configure your application server

Put the remaining `APP_SID` configuration info into your application server by opening `server.py` and setting the following constants with the information you gathered above.

    ACCOUNT_SID = 'AC***'
    API_KEY = 'SK***'
    API_KEY_SECRET = '***'
    APP_SID = 'AP***'

Once you’ve done that, restart the server so it uses the new configuration info. Now it's time to test.

Open up a browser and visit the URL for your application server's Access Token endpoint: `https://{YOUR-SERVER}/accessToken`. If everything is configured correctly, you should see a long string of letters and numbers, which is a Twilio Access Token. Your Android app will use a token like this to connect to Twilio.

### <a name="bullet6"></a>6. Run the app

Paste the Access Token into the VoiceActivity.java. 

<img width="700px" src="images/quickstart/paste_token.png"/>


Run the quickstart app on an Android device

<img height="500px" src="images/quickstart/voice_activity.png">"


Press the call button to connect to Twilio

<img height="500px" src="images/quickstart/voice_make_call.png">

### <a name="bullet7"></a>7. Generate `google-services.json` from the Google Developer Console

The Programmable Voice Android SDK uses Google Cloud Messaging push notifications to let your application know when it is receiving an incoming call. If you want your users to receive incoming calls, you’ll need to enable GCM in your application.

Follow the steps in the [Google Developers Console](https://developers.google.com/mobile/add). You must enter the correct Android package name, in this case  `com.twilio.voice.quickstart` .

<img width="700px" src="images/quickstart/create_choose_app.png">


Save the generated `Server API Key` and `Sender ID` in your notepad. You will need them in the next step.

<img width="700px" src="images/quickstart/save_server_api_key.png">"

Copy the generated `google-services.json` into the `/app` directory of the quickstart project to replace the existing `/app/google-services.json` .


### <a name="bullet8"></a>8. Add a Push Credential using your GCM `Server API Key` 

You will need to store the GCM `Server API Key` with Twilio so that we can send push notifications to your app on your behalf. Once you store the API Key with Twilio it will get assigned a Push Credential SID so that you can later specify which key we should use to send push notifications.

Go to the Push Credentials page and create a new Push Credential.

Paste in the `Server API Key` and press Save.

<img width="700px" src="images/quickstart/add_push_cred.png">"

### <a name="bullet9"></a>9. Receiving an Incoming Notification

Put the `PUSH_CREDENTIAL_SID` configuration info into your application server by opening `server.py` and setting the following constants with the information you gathered above.

    ACCOUNT_SID = 'AC***'
    API_KEY = 'SK***'
    API_KEY_SECRET = '***'
    PUSH_CREDENTIAL_SID = 'CR***'
    APP_SID = 'AP***'

Once you’ve done that, restart the server so it uses the new configuration info. Now it's time to test. Hit your application server's `placeCall` endpoint. This will trigger a Twilio REST API request that will make an inbound call to your mobile app. Once your app accepts the call, you should hear a congratulatory message.

<img height="500px" src="images/quickstart/incoming_notification.png">"





## Reducing APK Size

Our library is built using native libraries. As a result, if you use the default gradle build you will generate an APK with all four architectures(armeabi-v7a, arm64-v8a, x86, x86_64) in your APK.

[APK splits](https://developer.android.com/studio/build/configure-apk-splits.html) allow developers to build multiple APKs for different screen sizes and ABIs. Enabling APK splits ensures that the minimum amount of files required to support a particular device are packaged into an APK.

The following snippet shows an example `build.gradle` with APK splits enabled.

    apply plugin: 'com.android.application'

    android {
        compileSdkVersion 24
        buildToolsVersion "24.0.2"

        defaultConfig {
            applicationId "com.twilio.voice.quickstart"
            minSdkVersion 16
            targetSdkVersion 24
            versionCode 1
            versionName "1.0"
        }

        // Specify that we want to split up the APK based on ABI
        splits {
            abi {
                // Enable ABI split
                enable true

                // Clear list of ABIs
                reset()

                // Specify each architecture currently supported by the Video SDK
                include "armeabi-v7a", "arm64-v8a", "x86", "x86_64"

                // Specify that we do not want an additional universal SDK
                universalApk false
            }
        }
    }

    dependencies {
        compile 'com.twilio:voice-android:2.0.0-beta4'
    }

The adoption of APK splits requires developers to submit multiple APKs to the Play Store. Refer to [Google’s documentation](https://developer.android.com/google/play/publishing/multiple-apks.html)  for how to support this in your application.

## More Documentation

You can find more documentation on getting started as well as our latest Javadoc below:


* [Getting Started](https://www.twilio.com/docs/api/voice-sdk/android/getting-started)
* [Javadoc](https://media.twiliocdn.com/sdk/android/voice/latest/docs/)

## Issues and Support

Please file any issues you find here on Github.
For general inquiries related to the Voice SDK you can file a support ticket.

## License
MIT
