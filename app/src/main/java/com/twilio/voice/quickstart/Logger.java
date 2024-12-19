package com.twilio.voice.quickstart;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Vector;

class Logger extends OutputStream {
  private final String logTag;
  private final Vector<Character> logInfoBuffer = new Vector<>();
  public Logger(Class<?> clazz) {
    logTag = clazz.getSimpleName();
  }

  public void debug(final String message) {
    if (BuildConfig.DEBUG) {
      Log.d(logTag, message);
    }
  }

  public void log(final String message) {
    Log.i(logTag, message);
  }

  public void warning(final String message) {
    try {
      write(message.getBytes());
      flush();
    } catch (Exception ignore) {}
  }

  public void error(final String message) {
    Log.e(logTag, message);
  }

  public void warning(final Exception e, final String message) {
    PrintStream printStream = new PrintStream(this);
    printStream.println(message);
    e.printStackTrace(printStream);
    printStream.flush();
  }

  @Override
  public synchronized void write(int i) throws IOException {
    logInfoBuffer.add((char)i);
  }

  @Override
  public synchronized void write(byte[] b) throws IOException {
    for (char c: (new String(b, Charset.defaultCharset()).toCharArray())) {
      logInfoBuffer.add(c);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    for (char c: (new String(b, off, len, Charset.defaultCharset()).toCharArray())) {
      logInfoBuffer.add(c);
    }
  }

  @Override
  public synchronized void flush() throws IOException {
    char [] output = new char[logInfoBuffer.size()];
    for (int i = 0; i < logInfoBuffer.size(); ++i) {
      output[i] = logInfoBuffer.get(i);
    }
    logInfoBuffer.clear();
    Log.w(logTag, String.valueOf(output));
  }
}
