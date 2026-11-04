# Twilio Voice Custom Logger Example

The project demonstrates how to use Twilio's Programmable Voice SDK with a custom logger that writes the SDK's log messages to a file and, optionally, streams them to a websocket server.

**exampleCustomLogger** installs a `LoggerInterface` implementation with `Voice.setLogger(...)`. Every message the SDK emits is formatted by `CustomLogger` and written to Logcat and to a log file, and streamed to a websocket if one is configured.

- `CustomLoggerApplication` sets the SDK log levels and installs the logger in `onCreate()`, before anything else uses the SDK.
- `CustomLogger` implements `LoggerInterface`. It writes each message to Logcat and passes the formatted line to every `LogWriter` it has.
- `LogWriter` is the interface for a log destination. `FileLogWriter` and `WebSocketLogWriter` implement it.
- `FileLogWriter` appends lines to the file on a background thread, so logging never blocks the caller. When the file reaches 5 MB it is renamed to `twilio-voice.log.1` (replacing any previous one) and a new file is started, so the logs use at most about 10 MB.
- `WebSocketLogWriter` streams each line as a text message over a websocket (OkHttp). It is only installed when `logServerUrl` is set. Lines logged before the connection opens are buffered (up to 1000) and sent once it does, and it reconnects automatically if the connection drops.
- `CustomLoggerActivity` makes a call and shows the end of the log file, so you can see the messages arrive.

Each line in the file is `timestamp LEVEL tag message`, with the stack trace appended when there is one.

### Setup

Refer to the [README](https://github.com/twilio/voice-quickstart-android/blob/master/README.md) for instructions on how to generate an access token and make an outbound `Call`.
In [step 6](https://github.com/twilio/voice-quickstart-android#6-run-the-app), update the placeholder of `accessToken` with the access token string you copied in `CustomLoggerActivity.java`.

### Receiving incoming calls

Incoming calls arrive as FCM push notifications, so the module needs its own `google-services.json`. Follow the quickstart README for the Firebase and Twilio side:

1. [Generate `google-services.json`](https://github.com/twilio/voice-quickstart-android#1-generate-google-servicesjson) for the package name `com.twilio.examplecustomlogger`, and put it in the `exampleCustomLogger` directory (not `app`).
2. [Create a Push Credential](https://github.com/twilio/voice-quickstart-android#7-create-a-push-credential-using-your-fcm-service-account-key) from your FCM service account key, and add its SID to the access token's Voice grant.
3. [Trigger an incoming call](https://github.com/twilio/voice-quickstart-android#8-receiving-an-incoming-notification) with your server's `/place-call` endpoint.

`google-services.json` is gitignored, so it is never committed. If the file is missing the module still builds and can make outbound calls, but it cannot register for push or receive calls.

When a call comes in, `IncomingCallService` shows a notification with Accept and Reject actions, handled by `CallActionReceiver`.

### Running

Build and run the **exampleCustomLogger** module, make a call, and press **Refresh log** to see the SDK's log messages.

The log file is `twilio-voice.log` in the app's external files directory. The path is shown in the app. To copy it to your computer:

    adb pull /sdcard/Android/data/com.twilio.examplecustomlogger/files/logs/twilio-voice.log

If external storage is unavailable the file is written to the app's internal `files/logs` directory instead.

### Streaming logs to a websocket

The file is always written. To also stream the logs live, set `logServerUrl` to a websocket URL, either in `local.properties`:

    logServerUrl=ws://192.168.1.10:8080

or on the command line:

    ./gradlew :exampleCustomLogger:installDebug -PlogServerUrl=ws://192.168.1.10:8080

If it is not set, the websocket writer is not installed. Use a host the device can reach, not `localhost` (on the Android emulator, `10.0.2.2` is the host machine). Each websocket message is one log line in the same format as the file. Prefer `wss://` outside a local network, since the logs can contain call details.

### Choosing what is logged

The logger only receives the messages that pass the SDK's log level. `CustomLoggerApplication` sets it like this:

    Voice.setLogLevel(LogLevel.DEBUG);
    Voice.setModuleLogLevel(LogModule.SIGNALING, LogLevel.ALL);
    Voice.setModuleLogLevel(LogModule.WEBRTC, LogLevel.ALL);

Lower these levels to log less. The log file is not encrypted and can contain call details, so do not ship an app that writes verbose logs without deciding who may read them.
