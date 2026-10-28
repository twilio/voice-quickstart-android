package com.twilio.examplecustomlogger;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Appends log lines to a file on a background thread, so logging never blocks the caller. When the
 * file exceeds {@link #MAX_FILE_BYTES} it is renamed to <code>&lt;name&gt;.1</code> (replacing any
 * previous one) and a new file is started, which bounds disk usage to about twice that size.
 */
public class FileLogWriter {
    private static final String TAG = "FileLogWriter";
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final File logFile;
    private final File rotatedFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BufferedWriter writer;
    private long size;

    public FileLogWriter(@NonNull File logFile) {
        this.logFile = logFile;
        this.rotatedFile = new File(logFile.getPath() + ".1");
    }

    public void write(@NonNull final String line) {
        executor.execute(
                () -> {
                    try {
                        append(line);
                    } catch (IOException e) {
                        // Logcat only: logging this failure through the SDK logger would recurse.
                        Log.e(TAG, "Unable to write to " + logFile, e);
                        closeWriter();
                    }
                });
    }

    private void append(String line) throws IOException {
        if (writer == null) {
            File dir = logFile.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Unable to create " + dir);
            }
            size = logFile.length();
            writer = new BufferedWriter(new FileWriter(logFile, true));
        }
        if (size >= MAX_FILE_BYTES) {
            closeWriter();
            if (rotatedFile.exists() && !rotatedFile.delete()) {
                throw new IOException("Unable to delete " + rotatedFile);
            }
            if (!logFile.renameTo(rotatedFile)) {
                throw new IOException("Unable to rotate " + logFile);
            }
            size = 0;
            writer = new BufferedWriter(new FileWriter(logFile, true));
        }
        writer.write(line);
        writer.newLine();
        writer.flush();
        size += line.length() + 1;
    }

    private void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }
}
