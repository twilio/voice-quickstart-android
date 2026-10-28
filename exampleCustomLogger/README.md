# Twilio Voice Custom Logger Example

The project demonstrates how to use Twilio's Programmable Voice SDK with a custom logger that writes the SDK's log messages to a file.

**exampleCustomLogger** installs a `LoggerInterface` implementation with `Voice.setLogger(...)`. Every message the SDK emits is formatted by `CustomLogger` and written to Logcat and to a log file.

- `CustomLoggerApplication` sets the SDK log levels and installs the logger in `onCreate()`, before anything else uses the SDK.
- `CustomLogger` implements `LoggerInterface`. It writes each message to Logcat and, if it has one, to a `FileLogWriter`.
- `FileLogWriter` appends lines to the file on a background thread, so logging never blocks the caller. When the file reaches 5 MB it is renamed to `twilio-voice.log.1` (replacing any previous one) and a new file is started, so the logs use at most about 10 MB.
- `CustomLoggerActivity` makes a call and shows the end of the log file, so you can see the messages arrive.

Each line in the file is `timestamp LEVEL tag message`, with the stack trace appended when there is one.

### Setup

Refer to the [README](https://github.com/twilio/voice-quickstart-android/blob/master/README.md) for instructions on how to generate an access token and make an outbound `Call`.
In [step 6](https://github.com/twilio/voice-quickstart-android#6-run-the-app), update the placeholder of `accessToken` with the access token string you copied in `CustomLoggerActivity.java`.

### Running

Build and run the **exampleCustomLogger** module, make a call, and press **Refresh log** to see the SDK's log messages.

The log file is `twilio-voice.log` in the app's external files directory. The path is shown in the app. To copy it to your computer:

    adb pull /sdcard/Android/data/com.twilio.examplecustomlogger/files/logs/twilio-voice.log

If external storage is unavailable the file is written to the app's internal `files/logs` directory instead.

### Choosing what is logged

The logger only receives the messages that pass the SDK's log level. `CustomLoggerApplication` sets it like this:

    Voice.setLogLevel(LogLevel.DEBUG);
    Voice.setModuleLogLevel(LogModule.SIGNALING, LogLevel.ALL);
    Voice.setModuleLogLevel(LogModule.WEBRTC, LogLevel.ALL);

Lower these levels to log less. The log file is not encrypted and can contain call details, so do not ship an app that writes verbose logs without deciding who may read them.
