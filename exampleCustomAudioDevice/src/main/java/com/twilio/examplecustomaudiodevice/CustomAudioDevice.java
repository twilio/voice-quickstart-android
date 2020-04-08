package com.twilio.examplecustomaudiodevice;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.twilio.voice.AudioDevice;
import com.twilio.voice.AudioDeviceContext;
import com.twilio.voice.AudioFormat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import tvo.webrtc.voiceengine.WebRtcAudioUtils;

public class CustomAudioDevice implements AudioDevice {
    private static final String TAG = CustomAudioDevice.class.getSimpleName();
    private static final String MSG_KEY = "message_key";

    private Context context;
    private boolean keepAliveRendererRunnable = true;
    // Requested size of each recorded buffer provided to the client.
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int WAV_FILE_HEADER_SIZE = 44;
    private int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
    private static final int BUFFER_SIZE_FACTOR = 2;

    private ByteBuffer fileWriteByteBuffer;
    private int writeBufferSize;
    private InputStream inputStream;
    private DataInputStream dataInputStream;

    private AudioDeviceContext audioDeviceContext;
    private AudioRecord audioRecord;
    private ByteBuffer micWriteBuffer;
    private android.os.Handler capturerHandler;
    private final HandlerThread capturerThread;

    private ByteBuffer readByteBuffer;
    private AudioTrack audioTrack = null;
    private android.os.Handler rendererHandler;
    private final HandlerThread rendererThread;

    private boolean isMusicPlaying = true;

    enum MessageType {
        INIT_RENDERER,
        STOP_RENDERER,
        INIT_CAPTURER,
        STOP_CAPTURER
    };

    private final Runnable fileCapturerRunnable = () -> {
        int bytesRead;
        try {
            if (dataInputStream != null && (bytesRead = dataInputStream.read(fileWriteByteBuffer.array(), 0, writeBufferSize)) > -1) {
                if (bytesRead == fileWriteByteBuffer.capacity()) {
                    AudioDevice.audioDeviceWriteCaptureData(audioDeviceContext, fileWriteByteBuffer);
                } else {
                    processRemaining(fileWriteByteBuffer, fileWriteByteBuffer.capacity());
                    AudioDevice.audioDeviceWriteCaptureData(audioDeviceContext, fileWriteByteBuffer);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        capturerHandler.postDelayed(this.fileCapturerRunnable, CALLBACK_BUFFER_SIZE_MS);
    };

    private final Runnable microphoneCapturerRunnable = () -> {
        audioRecord.startRecording();
        while (true) {
            int bytesRead = audioRecord.read(micWriteBuffer, micWriteBuffer.capacity());
            if (bytesRead == micWriteBuffer.capacity()) {
                AudioDevice.audioDeviceWriteCaptureData(audioDeviceContext, micWriteBuffer);
            } else {
                String errorMessage = "AudioRecord.read failed: " + bytesRead;
                Log.e(TAG, errorMessage);
                if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    stopRecording();
                    Log.e(TAG, errorMessage);
                }
                break;
            }
        }
    };

    private final Runnable rendererRunnable = () -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        try {
            audioTrack.play();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioTrack.play failed: " + e.getMessage());
            this.releaseAudioResources();
            return;
        }

        while (keepAliveRendererRunnable) {
            AudioDevice.audioDeviceReadRenderData(audioDeviceContext, readByteBuffer);

            int bytesWritten = 0;
            if (WebRtcAudioUtils.runningOnLollipopOrHigher()) {
                bytesWritten = writeOnLollipop(audioTrack, readByteBuffer, readByteBuffer.capacity());
            } else {
                bytesWritten = writePreLollipop(audioTrack, readByteBuffer, readByteBuffer.capacity());
            }
            if (bytesWritten != readByteBuffer.capacity()) {
                Log.e(TAG, "AudioTrack.write failed: " + bytesWritten);
                if (bytesWritten == AudioTrack.ERROR_INVALID_OPERATION) {
                    keepAliveRendererRunnable = false;
                }
            }
            readByteBuffer.rewind();
        }
    };

    public CustomAudioDevice(Context context) {
        this.context = context;
        this.capturerThread = new HandlerThread("CapturerThread");
        this.capturerThread.start();
        this.capturerHandler = new android.os.Handler(capturerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                String message = bundle.getString(MSG_KEY);
                MessageType messageType = MessageType.valueOf(message);
                switch (messageType) {
                    case INIT_CAPTURER:
                        initCapturer();
                        break;
                    case STOP_CAPTURER:
                        stopCapturer();
                        break;
                    default:
                        break;
                }
            }
        };

        this.rendererThread = new HandlerThread("RendererThread");
        this.rendererThread.start();
        this.rendererHandler = new android.os.Handler(rendererThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                String message = bundle.getString(MSG_KEY);
                MessageType messageType = MessageType.valueOf(message);
                switch (messageType) {
                    case INIT_RENDERER:
                        initRenderer();
                        break;
                    case STOP_RENDERER:
                        stopRenderer();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public void switchInput(boolean playMusic) {
        isMusicPlaying = playMusic;
        if (playMusic) {
            initializeStreams();
            stopRecording();
            capturerHandler.post(fileCapturerRunnable);
        } else {
            capturerHandler.removeCallbacks(fileCapturerRunnable);
            capturerHandler.post(microphoneCapturerRunnable);
        }
    }

    private void initializeStreams() {
        inputStream = null;
        dataInputStream = null;
        inputStream = context.getResources().openRawResource(context.getResources().getIdentifier("music",
                "raw", context.getPackageName()));
        dataInputStream = new DataInputStream(inputStream);
        try {
            int bytes = dataInputStream.skipBytes(WAV_FILE_HEADER_SIZE);
            Log.d(TAG, "Number of bytes skipped : " + bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isMusicPlaying() {
        return isMusicPlaying;
    }

    @Nullable
    @Override
    public AudioFormat getCapturerFormat() {
        return new AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO);
    }

    @Override
    public boolean onInitCapturer() {
        sendMessage(capturerHandler, MessageType.INIT_CAPTURER);
        return true;
    }

    @Override
    public boolean onStartCapturing(@NonNull AudioDeviceContext audioDeviceContext) {
        audioDeviceContext = audioDeviceContext;
        capturerHandler.post(fileCapturerRunnable);
        return true;
    }

    @Override
    public boolean onStopCapturing() {
        sendMessage(capturerHandler, MessageType.STOP_CAPTURER);
        return true;
    }

    @Nullable
    @Override
    public AudioFormat getRendererFormat() {
        return new AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO);
    }

    @Override
    public boolean onInitRenderer() {
        sendMessage(rendererHandler, MessageType.INIT_RENDERER);
        return true;
    }

    @Override
    public boolean onStartRendering(@NonNull AudioDeviceContext audioDeviceContext) {
        this.audioDeviceContext = audioDeviceContext;
        rendererHandler.post(rendererRunnable);
        return true;
    }

    @Override
    public boolean onStopRendering() {
        sendMessage(rendererHandler, MessageType.STOP_RENDERER);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
        return audioTrack.write(byteBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
    }

    private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
        return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
    }

    // Helper methods
    private void sendMessage(Handler handler, MessageType message) {
        Message msg = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putString(MSG_KEY, String.valueOf(message));
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    private void initRenderer() {
        int bytesPerFrame = getRendererFormat().getChannelCount() * (BITS_PER_SAMPLE / 8);
        readByteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (getRendererFormat().getSampleRate() / BUFFERS_PER_SECOND));
        int channelConfig = channelCountToConfiguration(getRendererFormat().getChannelCount());
        int minBufferSize = AudioRecord.getMinBufferSize(getRendererFormat().getSampleRate(), channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, getRendererFormat().getSampleRate(), channelConfig,
                android.media.AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
        keepAliveRendererRunnable = true;
    }

    void stopRenderer() {
        try {
            audioTrack.stop();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioTrack.stop failed: " + e.getMessage());
        }
        audioTrack.flush();
        audioTrack.release();
    }

    void initCapturer() {
        int bytesPerFrame = 2 * (BITS_PER_SAMPLE / 8);
        int framesPerBuffer = getCapturerFormat().getSampleRate() / BUFFERS_PER_SECOND;
        int channelConfig = channelCountToConfiguration(getCapturerFormat().getChannelCount());
        int minBufferSize =
                AudioRecord.getMinBufferSize(getCapturerFormat().getSampleRate(),
                        channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT);
        micWriteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        int bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, micWriteBuffer.capacity());
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, getCapturerFormat().getSampleRate(),
                android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);

        fileWriteByteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        writeBufferSize = fileWriteByteBuffer.capacity();
        initializeStreams();
    }

    void stopCapturer() {
        if (isMusicPlaying) {
            closeStreams();
        } else {
            stopRecording();
        }
    }

    // Capturer helper methods
    private void closeStreams() {
        capturerHandler.removeCallbacks(fileCapturerRunnable);
        try {
            dataInputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        capturerHandler.removeCallbacks(microphoneCapturerRunnable);
        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioRecord.stop failed: " + e.getMessage());
        }
    }

    private int channelCountToConfiguration(int channels) {
        return (channels == 1 ? android.media.AudioFormat.CHANNEL_IN_MONO : android.media.AudioFormat.CHANNEL_IN_STEREO);
    }

    private void processRemaining(ByteBuffer bb, int chunkSize) {
        bb.position(bb.limit()); // move at the end
        bb.limit(chunkSize); // get ready to pad with longs
        while (bb.position() < chunkSize) {
            bb.putLong(0);
        }
        bb.limit(chunkSize);
        bb.flip();
    }

    // Renderer helper methods
    private void releaseAudioResources() {
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
    }
}