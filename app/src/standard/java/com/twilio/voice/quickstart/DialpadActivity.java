package com.twilio.voice.quickstart;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;
import android.widget.Toolbar;

import com.twilio.voice.Call;

import java.util.UUID;

public class DialpadActivity extends AppCompatActivity {
    private ToneGenerator toneGenerator;
    private EditText dialpadEditText;
    private final int[] digitIds = {R.id.button0, R.id.button1, R.id.button2, R.id.button3, R.id.button4, R.id.button5, R.id.button6, R.id.button7, R.id.button8, R.id.button9, R.id.buttonStar, R.id.buttonHash};
    private final String[] digits = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "#"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialpad);

        dialpadEditText = findViewById(R.id.dialpadEditText);
        Button buttonDone = findViewById(R.id.buttonDone);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80);

        for (int i = 0; i < digitIds.length; i++) {
            Button digitButton = findViewById(digitIds[i]);
            final String digit = digits[i];
            setDigitClickListener(digitButton, digit);
        }

        buttonDone.setOnClickListener(v -> {
            finish();
        });
    }

    private void setDigitClickListener(Button button, String digit) {
        Call activeCall = VoiceService.activeCall;
        button.setOnClickListener(v -> {
            dialpadEditText.append(String.valueOf(digit));
            if (activeCall != null) {
                activeCall.sendDigits(digit);
            } else {
                Log.w("DialpadActivity", "Active call is null");
            }
            playTone(digit);
        });
    }

    private void playTone(String digit) {
        int toneType;
        switch (digit) {
            case "0": toneType = ToneGenerator.TONE_DTMF_0; break;
            case "1": toneType = ToneGenerator.TONE_DTMF_1; break;
            case "2": toneType = ToneGenerator.TONE_DTMF_2; break;
            case "3": toneType = ToneGenerator.TONE_DTMF_3; break;
            case "4": toneType = ToneGenerator.TONE_DTMF_4; break;
            case "5": toneType = ToneGenerator.TONE_DTMF_5; break;
            case "6": toneType = ToneGenerator.TONE_DTMF_6; break;
            case "7": toneType = ToneGenerator.TONE_DTMF_7; break;
            case "8": toneType = ToneGenerator.TONE_DTMF_8; break;
            case "9": toneType = ToneGenerator.TONE_DTMF_9; break;
            case "*": toneType = ToneGenerator.TONE_DTMF_S; break;
            case "#": toneType = ToneGenerator.TONE_DTMF_P; break;
            default: return;
        }
        toneGenerator.startTone(toneType, 150);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
