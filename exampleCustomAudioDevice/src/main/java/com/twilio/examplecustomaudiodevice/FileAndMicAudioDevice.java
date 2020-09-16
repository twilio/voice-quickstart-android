package com.twilio.examplecustomaudiodevice;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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

public class FileAndMicAudioDevice implements AudioDevice {
    private static final String TAG = FileAndMicAudioDevice.class.getSimpleName();
    // TIMEOUT for rendererThread and capturerThread to wait for successful call to join()
    private static final long THREAD_JOIN_TIMEOUT_MS = 2000;

    private Context context;
    private boolean keepAliveRendererRunnable = true;
    // We want to get as close to 10 msec buffers as possible because this is what the media engine prefers.
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;
    // Default audio data format is PCM 16 bit per sample. Guaranteed to be supported by all devices.
    private static final int BITS_PER_SAMPLE = 16;
    // Average number of callbacks per second.
    private int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;
    // Ask for a buffer size of BUFFER_SIZE_FACTOR * (minimum required buffer size). The extra space
    // is allocated to guard against glitches under high load.
    private static final int BUFFER_SIZE_FACTOR = 2;
    private static final int WAV_FILE_HEADER_SIZE = 44;

    private ByteBuffer fileWriteByteBuffer;
    private int writeBufferSize;
    private InputStream inputStream;
    private DataInputStream dataInputStream;

    private AudioRecord audioRecord;
    private ByteBuffer micWriteBuffer;

    private ByteBuffer readByteBuffer;
    private AudioTrack audioTrack = null;

    // Handlers and Threads
    private Handler capturerHandler;
    private HandlerThread capturerThread;
    private Handler rendererHandler;
    private HandlerThread rendererThread;

    private AudioDeviceContext renderingAudioDeviceContext;
    private AudioDeviceContext capturingAudioDeviceContext;
    // By default music capturer is enabled
    private boolean isMusicPlaying = true;

    /*
     * This Runnable reads a music file and provides the audio frames to the AudioDevice API via
     * AudioDevice.audioDeviceWriteCaptureData(..) until there is no more data to be read, the
     * capturer input switches to the microphone, or the call ends.
     */
    private final Runnable fileCapturerRunnable = () -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        int bytesRead;
        try {
            if (dataInputStream != null && (bytesRead = dataInputStream.read(fileWriteByteBuffer.array(), 0, writeBufferSize)) > -1) {
                if (bytesRead == fileWriteByteBuffer.capacity()) {
                    AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext, fileWriteByteBuffer);
                } else {
                    processRemaining(fileWriteByteBuffer, fileWriteByteBuffer.capacity());
                    AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext, fileWriteByteBuffer);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        capturerHandler.postDelayed(this.fileCapturerRunnable, CALLBACK_BUFFER_SIZE_MS);
    };

    /*
     * This Runnable reads data from the microphone and provides the audio frames to the AudioDevice
     * API via AudioDevice.audioDeviceWriteCaptureData(..) until the capturer input switches to
     * microphone or the call ends.
     */
    private final Runnable microphoneCapturerRunnable = () -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        audioRecord.startRecording();
        while (true) {
            int bytesRead = audioRecord.read(micWriteBuffer, micWriteBuffer.capacity());
            if (bytesRead == micWriteBuffer.capacity()) {
                AudioDevice.audioDeviceWriteCaptureData(capturingAudioDeviceContext, micWriteBuffer);
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

    /*
     * This Runnable reads audio data from the callee perspective via AudioDevice.audioDeviceReadRenderData(...)
     * and plays out the audio data using AudioTrack.write().
     */
    private final Runnable speakerRendererRunnable = () -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        try {
            audioTrack.play();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioTrack.play failed: " + e.getMessage());
            this.releaseAudioResources();
            return;
        }

        while (keepAliveRendererRunnable) {
            // Get 10ms of PCM data from the SDK. Audio data is written into the ByteBuffer provided.
            AudioDevice.audioDeviceReadRenderData(renderingAudioDeviceContext, readByteBuffer);

            int bytesWritten = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bytesWritten = writeOnLollipop(audioTrack, readByteBuffer, readByteBuffer.capacity());
            } else {
                bytesWritten = writePreLollipop(audioTrack, readByteBuffer, readByteBuffer.capacity());
            }
            if (bytesWritten != readByteBuffer.capacity()) {
                Log.e(TAG, "AudioTrack.write failed: " + bytesWritten);
                if (bytesWritten == AudioTrack.ERROR_INVALID_OPERATION) {
                    keepAliveRendererRunnable = false;
                    break;
                }
            }
            // The byte buffer must be rewinded since byteBuffer.position() is increased at each
            // call to AudioTrack.write(). If we don't do this, will fail the next  AudioTrack.write().
            readByteBuffer.rewind();
        }
    };

    public FileAndMicAudioDevice(Context context) {
        this.context = context;
    }

    /*
     * This method enables a capturer switch between a file and the microphone.
     * @param playMusic
     */
    public void switchInput(boolean playMusic) {
        isMusicPlaying = playMusic;
        if (playMusic) {
            initializeStreams();
            capturerHandler.removeCallbacks(microphoneCapturerRunnable);
            stopRecording();
            capturerHandler.post(fileCapturerRunnable);
        } else {
            capturerHandler.removeCallbacks(fileCapturerRunnable);
            capturerHandler.post(microphoneCapturerRunnable);
        }
    }

    public boolean isMusicPlaying() {
        return isMusicPlaying;
    }

    /*
     * Return the AudioFormat used the capturer. This custom device uses 44.1kHz sample rate and
     * STEREO channel configuration both for microphone and the music file.
     */
    @Nullable
    @Override
    public AudioFormat getCapturerFormat() {
        return new AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO);
    }

    /*
     * Init the capturer using the AudioFormat return by getCapturerFormat().
     */
    @Override
    public boolean onInitCapturer() {
        int bytesPerFrame = 2 * (BITS_PER_SAMPLE / 8);
        int framesPerBuffer = getCapturerFormat().getSampleRate() / BUFFERS_PER_SECOND;
        // Calculate the minimum buffer size required for the successful creation of
        // an AudioRecord object, in byte units.
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
        // Initialize the streams.
        initializeStreams();
        return true;
    }

    @Override
    public boolean onStartCapturing(@NonNull AudioDeviceContext audioDeviceContext) {
        // Initialize the AudioDeviceContext
        this.capturingAudioDeviceContext = audioDeviceContext;
        // Create the capturer thread and start
        capturerThread = new HandlerThread("CapturerThread");
        capturerThread.start();
        // Create the capturer handler that processes the capturer Runnables.
        capturerHandler = new Handler(capturerThread.getLooper());
        capturerHandler.post(fileCapturerRunnable);
        return true;
    }

    @Override
    public boolean onStopCapturing() {
        if (isMusicPlaying) {
            closeStreams();
        } else {
            stopRecording();
        }
        /*
         * When onStopCapturing is called, the AudioDevice API expects that at the completion
         * of the callback the capturer has completely stopped. As a result, quit the capturer
         * thread and explicitly wait for the thread to complete.
         */
        capturerThread.quit();
        if (!tvo.webrtc.ThreadUtils.joinUninterruptibly(capturerThread, THREAD_JOIN_TIMEOUT_MS)) {
            Log.e(TAG, "Join of capturerThread timed out");
            return false;
        }
        return true;
    }

    /*
     * Return the AudioFormat used the renderer. This custom device uses 44.1kHz sample rate and
     * STEREO channel configuration for audio track.
     */
    @Nullable
    @Override
    public AudioFormat getRendererFormat() {
        return new AudioFormat(AudioFormat.AUDIO_SAMPLE_RATE_44100,
                AudioFormat.AUDIO_SAMPLE_STEREO);
    }

    @Override
    public boolean onInitRenderer() {
        int bytesPerFrame = getRendererFormat().getChannelCount() * (BITS_PER_SAMPLE / 8);
        readByteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (getRendererFormat().getSampleRate() / BUFFERS_PER_SECOND));
        int channelConfig = channelCountToConfiguration(getRendererFormat().getChannelCount());
        int minBufferSize = AudioRecord.getMinBufferSize(getRendererFormat().getSampleRate(), channelConfig, android.media.AudioFormat.ENCODING_PCM_16BIT);
        audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, getRendererFormat().getSampleRate(), channelConfig,
                android.media.AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);
        keepAliveRendererRunnable = true;
        return true;
    }

    @Override
    public boolean onStartRendering(@NonNull AudioDeviceContext audioDeviceContext) {
        this.renderingAudioDeviceContext = audioDeviceContext;
        // Create the renderer thread and start
        rendererThread = new HandlerThread("RendererThread");
        rendererThread.start();
        // Create the capturer handler that processes the renderer Runnables.
        rendererHandler = new Handler(rendererThread.getLooper());
        rendererHandler.post(speakerRendererRunnable);
        return true;
    }

    @Override
    public boolean onStopRendering() {
        stopAudioTrack();
        // Quit the rendererThread's looper to stop processing any further messages.
        rendererThread.quit();
        /*
         * When onStopRendering is called, the AudioDevice API expects that at the completion
         * of the callback the renderer has completely stopped. As a result, quit the renderer
         * thread and explicitly wait for the thread to complete.
         */
        if (!tvo.webrtc.ThreadUtils.joinUninterruptibly(rendererThread, THREAD_JOIN_TIMEOUT_MS)) {
            Log.e(TAG, "Join of rendererThread timed out");
            return false;
        }
        return true;
    }

    // Capturer helper methods
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

    private void closeStreams() {
        Log.d(TAG, "Remove any pending posts of fileCapturerRunnable that are in the message queue ");
        capturerHandler.removeCallbacks(fileCapturerRunnable);
        try {
            dataInputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        Log.d(TAG, "Remove any pending posts of microphoneCapturerRunnable that are in the message queue ");
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
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int writeOnLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
        return audioTrack.write(byteBuffer, sizeInBytes, AudioTrack.WRITE_BLOCKING);
    }

    private int writePreLollipop(AudioTrack audioTrack, ByteBuffer byteBuffer, int sizeInBytes) {
        return audioTrack.write(byteBuffer.array(), byteBuffer.arrayOffset(), sizeInBytes);
    }

    void stopAudioTrack() {
        keepAliveRendererRunnable = false;
        Log.d(TAG, "Remove any pending posts of speakerRendererRunnable that are in the message queue ");
        rendererHandler.removeCallbacks(speakerRendererRunnable);
        try {
            audioTrack.stop();
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioTrack.stop failed: " + e.getMessage());
        }
        releaseAudioResources();
    }

    private void releaseAudioResources() {
        if (audioTrack != null) {
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }
}
