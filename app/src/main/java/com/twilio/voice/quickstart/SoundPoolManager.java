package com.twilio.voice.quickstart;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;


import static android.content.Context.AUDIO_SERVICE;

import static java.lang.String.format;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressLint("DefaultLocale")
class SoundPoolManager {
    enum Sound {
        RINGER,
        DISCONNECT
    }

    enum SoundState {
        LOADING,
        READY,
        PLAYING,
        ERROR
    }

    private static class SoundRecord {
        final int id;
        final boolean loop;
        SoundState state;

        public SoundRecord(Context context,
                           SoundPool soundPool,
                           final int resource,
                           final boolean loop) {
            this.id = soundPool.load(context, resource, 1);
            this.state = SoundState.LOADING;
            this.loop = loop;
        }
    }

    private static final Logger log = new Logger(SoundPoolManager.class);
    private final float volume;
    private final SoundPool soundPool;

    private Map<Sound, SoundRecord> soundBank;
    private int lastActiveAudioStreamId;

    SoundPoolManager(final Context context) {
        // construct sound pool
        soundPool = new SoundPool.Builder().setMaxStreams(1).build();
        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            for (Map.Entry<Sound, SoundRecord> entry : soundBank.entrySet()) {
                final SoundRecord record = entry.getValue();
                if (record.id == sampleId) {
                    record.state = (0 == status) ? SoundState.READY : SoundState.ERROR;
                    if (0 != status) {
                        log.error(
                                format("Failed to load sound %s, error: %d",
                                        entry.getKey().name(), status));
                    }
                }
            }
        });

        // construct sound bank & load
        soundBank = new HashMap<>() {{
            put(Sound.RINGER, new SoundRecord(context, soundPool, R.raw.incoming, true));
            put(Sound.DISCONNECT, new SoundRecord(context, soundPool, R.raw.disconnect, false));
        }};

        // no active stream
        lastActiveAudioStreamId = -1;

        // AudioManager audio settings for adjusting the volume
        AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = actualVolume / maxVolume;
    }

    void playSound(final Sound sound) {
        final SoundRecord soundRecord = Objects.requireNonNull(soundBank.get(sound));
        if (!isSoundPlaying() && SoundState.READY == soundRecord.state) {
            lastActiveAudioStreamId = soundPool.play(
                    soundRecord.id, volume, volume, 1, soundRecord.loop ? -1 : 0, 1f);
            soundRecord.state = soundRecord.loop ? SoundState.PLAYING : SoundState.READY;
        } else if (isSoundPlaying()) {
            log.warning(
                    format("cannot play sound %s: %d sound stream already active",
                            sound.name(), lastActiveAudioStreamId));
        } else {
            log.warning(format("cannot play sound %s: invalid state", sound.name()));
        }
    }

    void stopSound(final Sound sound) {
        final SoundRecord soundRecord = Objects.requireNonNull(soundBank.get(sound));
        if (SoundState.PLAYING == soundRecord.state) {
            soundPool.stop(lastActiveAudioStreamId);
        } else {
            log.warning(format("cannot stop sound %s: invalid state", sound.name()));
        }
    }

    @Override
    protected void finalize() throws Throwable {
        for (SoundRecord record : soundBank.values()) {
            switch (record.state) {
                case PLAYING:
                    soundPool.stop(lastActiveAudioStreamId);
                    // intentionally fall through
                case READY:
                    soundPool.unload(record.id);
                    break;
            }
        }
        soundPool.release();
        super.finalize();
    }

    private boolean isSoundPlaying() {
        boolean playbackActive = false;
        for (SoundRecord record : soundBank.values()) {
            playbackActive |= (SoundState.PLAYING == record.state);
        }
        return playbackActive;
    }
}
