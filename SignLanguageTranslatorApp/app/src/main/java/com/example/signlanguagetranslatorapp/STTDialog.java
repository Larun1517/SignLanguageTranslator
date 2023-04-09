package com.example.signlanguagetranslatorapp;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class STTDialog extends Dialog {
    Intent intent;
    SpeechRecognizer mRecognizer;
    private Button view_confirm;
    private ImageButton view_sttBtn;
    private TextView view_text;

    public STTDialog(@NonNull Context context) {
        super(context);
        WindowManager.LayoutParams blur = new WindowManager.LayoutParams();
        blur.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        blur.dimAmount = 0.8f;
        getWindow().setAttributes(blur);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setContentView(R.layout.dialog_stt);

        intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");

        view_confirm = findViewById(R.id.button_confirm);
        view_confirm.setOnClickListener(v -> { dismiss(); });

        view_text = (TextView)findViewById(R.id.sttResult);
        view_sttBtn = (ImageButton) findViewById(R.id.button_record);
        view_sttBtn.setOnClickListener(v -> {
            view_sttBtn.setImageResource(R.drawable.icon_mic_off);

            mRecognizer=SpeechRecognizer.createSpeechRecognizer(context);
            mRecognizer.setRecognitionListener(listener);
            mRecognizer.startListening(intent);
        });
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {}

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {}

        @Override
        public void onResults(Bundle results) {
            view_sttBtn.setImageResource(R.drawable.icon_mic_on);
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            for(int i = 0; i < matches.size() ; i++){
                view_text.setText(matches.get(i));
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };
}
