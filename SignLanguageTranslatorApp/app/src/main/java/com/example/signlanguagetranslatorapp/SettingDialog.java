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

public class SettingDialog extends Dialog {
    private EditText view_ip;
    private SeekBar view_ttsPitch;
    private SeekBar view_ttsRate;
    private Button view_confirm;

    private String set_ip;
    private int set_ttsPitch;
    private int set_ttsRate;

    public SettingDialog(@NonNull Context context, String ip, Float ttsPitch, Float ttsRate) {
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
        view_ttsPitch.setProgress((int) (ttsPitch*10));
        view_ttsRate = findViewById(R.id.seekBar_ttsRate);
        view_ttsRate.setProgress((int) (ttsRate*10));


        view_confirm = findViewById(R.id.button_confirm);
        view_confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                set_ip = view_ip.getText().toString();
                set_ttsPitch = view_ttsPitch.getProgress();
                set_ttsRate = view_ttsRate.getProgress();

                //이게맞나 데이터 옮겨야하는데
                dismiss();
            }
        });
    }
}
