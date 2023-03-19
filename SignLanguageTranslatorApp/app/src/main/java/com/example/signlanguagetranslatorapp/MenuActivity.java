package com.example.signlanguagetranslatorapp;

/**
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Main activity of MediaPipe Hands app. */
public class MenuActivity extends AppCompatActivity {
    // setting
    String ip = "211.106.58.219";
    Float ttsPitch = 0.8f;
    Float ttsRate = 1.0f;

    // Views
    /*
    LinearLayout StartCameraFront = findViewById(R.id.start_button_camera_front);
    LinearLayout StartCameraBack = findViewById(R.id.start_button_camera_back);
    LinearLayout StartSTT = findViewById(R.id.start_button_stt);
    Button Setting = findViewById(R.id.button_setting);
    */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();



        /** 상대방의 수어 번역하기 */
        LinearLayout StartCameraBack = findViewById(R.id.start_button_camera_back);
        StartCameraBack.setOnClickListener( v -> {
            Intent intent = new Intent(getBaseContext(), CameraActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("ttsPitch", ttsPitch);
            intent.putExtra("ttsRate", ttsRate);
            intent.putExtra("cameraMode", 0);
            startActivity(intent);
        });

        /** 나의 수어 번역하기 */
        LinearLayout StartCameraFront = findViewById(R.id.start_button_camera_front);
        StartCameraFront.setOnClickListener( v -> {
            Intent intent = new Intent(getBaseContext(), CameraActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("ttsPitch", ttsPitch);
            intent.putExtra("ttsRate", ttsRate);
            intent.putExtra("cameraMode", 1);
            startActivity(intent);
        });

        /** 목소리를 글자로 바꾸기 */
        LinearLayout StartSTT = findViewById(R.id.start_button_stt);
        StartSTT.setOnClickListener( v -> {
            // STT 액티비티
        });

        /** 어플리케이션 설정 */
        Button Setting = findViewById(R.id.button_setting);
        Setting.setOnClickListener( v -> {
            SettingDialog settingDialog = new SettingDialog(MenuActivity.this, ip, ttsPitch, ttsRate);
            settingDialog.setCancelable(false);
            settingDialog.getWindow().setGravity(Gravity.CENTER);
            settingDialog.show();
        });

    }
    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("안내");
        builder.setMessage("앱을 종료하시겠습니까?");

        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.setNeutralButton("아니오", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {}
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }



}