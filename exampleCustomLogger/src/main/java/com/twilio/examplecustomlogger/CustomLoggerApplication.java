package com.twilio.examplecustomlogger;

import android.app.Application;
import com.twilio.voice.LogLevel;
import com.twilio.voice.LogModule;
import com.twilio.voice.Voice;
import java.io.File;

public class CustomLoggerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Install the logger before anything else uses the SDK so that no message is missed.
         */
        Voice.setLogLevel(LogLevel.DEBUG);
        Voice.setModuleLogLevel(LogModule.SIGNALING, LogLevel.ALL);
        Voice.setModuleLogLevel(LogModule.WEBRTC, LogLevel.ALL);
        Voice.setLogger(new CustomLogger(new FileLogWriter(getLogFile())));
    }

    /**
     * The log file, under the app's external files directory so it can be pulled without root:
     * <code>adb pull /sdcard/Android/data/com.twilio.examplecustomlogger/files/logs/twilio-voice.log</code>.
     * Falls back to internal storage when external storage is unavailable.
     */
    File getLogFile() {
        File dir = getExternalFilesDir("logs");
        if (dir == null) {
            dir = new File(getFilesDir(), "logs");
        }
        return new File(dir, "twilio-voice.log");
    }
}
