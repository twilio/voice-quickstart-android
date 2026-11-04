package com.twilio.examplecustomlogger;

import androidx.annotation.NonNull;

public interface LogWriter {
    void write(@NonNull String line);
}
