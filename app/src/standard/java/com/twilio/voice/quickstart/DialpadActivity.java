package com.twilio.voice.quickstart;

import static com.twilio.voice.quickstart.VoiceApplication.voiceService;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;
import android.view.View;

import java.util.UUID;

public class DialpadActivity extends AppCompatActivity {
    private ToneGenerator toneGenerator;
    private EditText dialpadEditText;
    private final int DTMF_TONE_VOLUME = 80;
    private final int DTMF_TONE_PLAYBACK_DURATION_MS = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialpad);

        dialpadEditText = findViewById(R.id.dialpadEditText);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, DTMF_TONE_VOLUME);
    }

    public void onDoneTapped(View view) {
        finish();
    }

    public void onDigitTapped(View view) {
        Button digitButton = (Button) view;
        String digit = digitButton.getText().toString();
        playTone(digit);
        appendAndSendDigit(digit);
    }

    private void appendAndSendDigit(String digit) {
        UUID receivedUuid = (UUID)getIntent().getSerializableExtra("activeCallId");
        dialpadEditText.append(String.valueOf(digit));
        if (receivedUuid != null) {
            voiceService(voiceService -> voiceService.sendDigitToCall(receivedUuid, digit));
        } else {
            Log.w("DialpadActivity", "Active Call ID is null");
        }
    }

    private void playTone(String digit) {
        int toneType;
        switch (digit) {
            case "0":
                toneType = ToneGenerator.TONE_DTMF_0;
                break;
            case "1":
                toneType = ToneGenerator.TONE_DTMF_1;
                break;
            case "2":
                toneType = ToneGenerator.TONE_DTMF_2;
                break;
            case "3":
                toneType = ToneGenerator.TONE_DTMF_3;
                break;
            case "4":
                toneType = ToneGenerator.TONE_DTMF_4;
                break;
            case "5":
                toneType = ToneGenerator.TONE_DTMF_5;
                break;
            case "6":
                toneType = ToneGenerator.TONE_DTMF_6;
                break;
            case "7":
                toneType = ToneGenerator.TONE_DTMF_7;
                break;
            case "8":
                toneType = ToneGenerator.TONE_DTMF_8;
                break;
            case "9":
                toneType = ToneGenerator.TONE_DTMF_9;
                break;
            case "*":
                toneType = ToneGenerator.TONE_DTMF_S;
                break;
            case "#":
                toneType = ToneGenerator.TONE_DTMF_P;
                break;
            default:
                return;
        }
        toneGenerator.startTone(toneType, DTMF_TONE_PLAYBACK_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
