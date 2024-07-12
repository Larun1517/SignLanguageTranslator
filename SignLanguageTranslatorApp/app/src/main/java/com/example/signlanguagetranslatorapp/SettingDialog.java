package com.example.signlanguagetranslatorapp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.annotation.NonNull;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class SettingDialog extends Dialog {
    private EditText view_ip;
    private SeekBar view_ttsPitch;
    private SeekBar view_ttsRate;
    private Button view_confirm;
    private EditText view_termWord;
    private EditText view_termSentence;

    public SettingDialog(@NonNull Context context, String ip, int ttsPitch, int ttsRate, double termWord, double termSentence) {
        super(context);
        WindowManager.LayoutParams blur = new WindowManager.LayoutParams();
        blur.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        blur.dimAmount = 0.8f;
        getWindow().setAttributes(blur);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setContentView(R.layout.dialog_setting);

        view_ip = findViewById(R.id.edittext_ip);
        view_ip.setText(ip);
        view_ttsPitch = findViewById(R.id.seekBar_ttsPitch);
        view_ttsPitch.setProgress(ttsPitch);
        view_ttsRate = findViewById(R.id.seekBar_ttsRate);
        view_ttsRate.setProgress(ttsRate);
        view_termWord = findViewById(R.id.edittext_termWord);
        view_termWord.setText(Double.toString(termWord));
        view_termSentence = findViewById(R.id.edittext_termSentence);
        view_termSentence.setText(Double.toString(termSentence));

        view_confirm = findViewById(R.id.button_confirm);
        view_confirm.setOnClickListener(v -> {
            try (FileOutputStream fos = context.openFileOutput("setting.dat", Context.MODE_PRIVATE)) {
                DataOutputStream dos = new DataOutputStream(fos);
                dos.writeUTF((
                        view_ip.getText().toString() + "/" +
                        view_ttsPitch.getProgress() + "/" +
                        view_ttsRate.getProgress() + "/" +
                        view_termWord.getText().toString() + "/" +
                        view_termSentence.getText().toString()
                ));
                dos.close();
            } catch (IOException e) { e.printStackTrace(); }

            dismiss();
        });
    }
}