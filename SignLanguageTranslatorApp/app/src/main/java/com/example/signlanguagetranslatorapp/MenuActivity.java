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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MenuActivity extends AppCompatActivity {
    // setting
    private String ip = "211.114.76.163";
    private int ttsPitch = 8;
    private int ttsRate = 10;
    private double termWord = 2;
    private double termSentence = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        // 앱 필요 권한 확인 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 101);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},101);
        }

        // 상대방의 수어 번역하기
        LinearLayout StartCameraBack = findViewById(R.id.start_button_camera_back);
        StartCameraBack.setOnClickListener( v -> {
            readSetting();

            Intent intent = new Intent(getBaseContext(), CameraActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("ttsPitch", ttsPitch);
            intent.putExtra("ttsRate", ttsRate);
            intent.putExtra("termWord", termWord);
            intent.putExtra("termSentence", termSentence);
            intent.putExtra("cameraMode", 0);
            startActivity(intent);
        });

        // 나의 수어 번역하기
        LinearLayout StartCameraFront = findViewById(R.id.start_button_camera_front);
        StartCameraFront.setOnClickListener( v -> {
            readSetting();

            Intent intent = new Intent(getBaseContext(), CameraActivity.class);
            intent.putExtra("ip", ip);
            intent.putExtra("ttsPitch", ttsPitch);
            intent.putExtra("ttsRate", ttsRate);
            intent.putExtra("termWord", termWord);
            intent.putExtra("termSentence", termSentence);
            intent.putExtra("cameraMode", 1);
            startActivity(intent);
        });

        /*
        // 목소리를 글자로 바꾸기
        LinearLayout StartSTT = findViewById(R.id.start_button_stt);
        StartSTT.setOnClickListener( v -> {
            // STT 액티비티
            Intent intent = new Intent(getApplicationContext(), STTActivity.class);
            startActivity(intent);
        });
        */

        // 어플리케이션 설정
        Button Setting = findViewById(R.id.button_setting);
        Setting.setOnClickListener( v -> {
            readSetting();

            SettingDialog settingDialog = new SettingDialog(MenuActivity.this, ip, ttsPitch, ttsRate, termWord, termSentence);
            settingDialog.setCancelable(true);
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

        builder.setPositiveButton("예", (dialog, which) -> finish());
        builder.setNeutralButton("아니오", (dialog, which) -> {});

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void readSetting() {
        try {
            FileInputStream fis = openFileInput("setting.dat");
            DataInputStream dis = new DataInputStream(fis);
            String[] data = dis.readUTF().split("/");
            ip = data[0];
            ttsPitch = Integer.parseInt(data[1]);
            ttsRate = Integer.parseInt(data[2]);
            termWord = Double.parseDouble(data[3]);
            termSentence = Double.parseDouble(data[4]);
            dis.close();
        } catch (FileNotFoundException e) {
            try (FileOutputStream fos = MenuActivity.this.openFileOutput("setting.dat", Context.MODE_PRIVATE)) {
                DataOutputStream dos = new DataOutputStream(fos);
                dos.writeUTF("211.114.76.163/8/10/2/6");
                dos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}