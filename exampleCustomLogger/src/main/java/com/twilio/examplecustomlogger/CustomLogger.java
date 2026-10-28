package com.twilio.examplecustomlogger;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.twilio.voice.LogLevel;
import com.twilio.voice.LogModule;
import com.twilio.voice.LogParameters;
import com.twilio.voice.LoggerInterface;
import java.util.Locale;

/**
 * A {@link LoggerInterface} that receives every log message the Voice SDK emits. It writes each
 * one to Logcat and, when a {@link FileLogWriter} is given, to a file as well.
 *
 * <p>Install it with {@code Voice.setLogger(...)}. The SDK's log level still decides which
 * messages reach it: see {@code Voice.setLogLevel(...)} and {@code Voice.setModuleLogLevel(...)}.
 */
public class CustomLogger implements LoggerInterface {
    private final FileLogWriter fileWriter;

    /** Logs to Logcat only. */
    public CustomLogger() {
        this(null);
    }

    /** Logs to Logcat and, when {@code fileWriter} is not null, also to a file. */
    public CustomLogger(@Nullable FileLogWriter fileWriter) {
        this.fileWriter = fileWriter;
    }

    @Override
    public void log(@NonNull final LogParameters parameters) {
        String prefix = "[" + convertModuleToString(parameters.module) + "] ";
        if (!parameters.thread.isEmpty()) {
            prefix += "(thread " + parameters.thread + ") ";
        }
        if (!parameters.file.isEmpty()) {
            // Remove the path before the filename
            int index = parameters.file.lastIndexOf('/');
            prefix += parameters.file.substring(index + 1) + " ";
            if (parameters.line > 0) {
                prefix += "(line " + parameters.line + ") ";
            }
        }
        if (!parameters.function.isEmpty()) {
            prefix += parameters.function + ": ";
        }

        String tag = parameters.tag.isEmpty() ? "Twilio" : "Twilio:" + parameters.tag;
        String message = prefix + parameters.message;
        if (parameters.tr != null) {
            message += "\n" + Log.getStackTraceString(parameters.tr);
        }

        if (fileWriter != null && parameters.level != LogLevel.OFF) {
            fileWriter.write(
                    String.format(
                            Locale.US,
                            "%s %-7s %s %s",
                            parameters.timestamp,
                            parameters.level,
                            tag,
                            message));
        }

        switch (parameters.level) {
            case FATAL:
            case ERROR:
                Log.e(tag, message);
                break;
            case WARNING:
                Log.w(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case DEBUG:
                Log.d(tag, message);
                break;
            case TRACE:
            case ALL:
                Log.v(tag, message);
                break;
            case OFF:
            default:
                break;
        }
    }

    private String convertModuleToString(LogModule module) {
        switch (module) {
            case CORE:
                return "Core";
            case SIGNALING:
                return "Signaling";
            case WEBRTC:
                return "WebRTC";
            case PLATFORM:
                return "Platform";
            default:
                return "";
        }
    }
}
