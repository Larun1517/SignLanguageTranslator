package com.example.signlanguagetranslatorapp;

// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.exifinterface.media.ExifInterface;
// ContentResolver dependency
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

//import org.tensorflow.lite.Interpreter;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Hands hands;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    public MainActivity() throws IOException {
    }

    private enum InputSource {
        UNKNOWN,
        VIDEO,
        CAMERA,
    }
    private InputSource inputSource = InputSource.UNKNOWN;

    // Image demo UI and image loader components.
    private ActivityResultLauncher<Intent> imageGetter;
    // Video demo UI and video loader components.
    private VideoInput videoInput;
    private ActivityResultLauncher<Intent> videoGetter;
    // Live camera demo UI and camera components.
    private CameraInput cameraInput;

    private SolutionGlSurfaceView<HandsResult> glSurfaceView;

    // tflite
    String[] actions = new String[] {
            "1시","2시","3시","4시","5시","6시","7시","8시","9시","10시","11시","12시",
            "오후","오전","만나다","내일","오늘","좋아","나","너","바쁘다","카페","안돼","왜","전화받다","전화걸다","안녕"
    };
    String[] action_seq = new String[] {"", "", ""};
    String[] sentence = new String[] {"","","","","","","","","","","","","","","","","","","",""};
    int sentence_index = 0;
    String word = "?";
    Long term = System.currentTimeMillis()-6000;
    //float[][][] input = new float[1][10][104];

    int dataSize = 10 -1;
    String[] stackedData = new String[dataSize+1];

    // connection
    private Socket client;
    private DataOutputStream dataOutput;
    private DataInputStream dataInput;
    private String ip = "";
    private int port = 8080;
    private int bufferSize = 256;
    private static String CONNECT_MSG = "connect";
    private String STOP_MSG = ""; //stop

    // tts
    private TextToSpeech tts;

    // Views
    /*
    Button startCameraButton = findViewById(R.id.button_start_camera);
    Button ServerButton = findViewById(R.id.button_connect);
    TextView result1 = (TextView)findViewById(R.id.result1);
    TextView result2 = (TextView)findViewById(R.id.result2);
    TextView result3 = (EditText)findViewById(R.id.result3);
    */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.KOREA);
                    //tts.setLanguage(Locale.ENGLISH);
                }
            }
        });

        EditText inputIP = (EditText)findViewById(R.id.inputIP);

        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setVisibility(View.GONE);
        setupLiveDemoUiComponents();

        Button ServerButton = findViewById(R.id.button_connect);
        ServerButton.setOnClickListener(
                v -> {
                    // 서버 연결
                    //Connect connect = new Connect();
                    //connect.execute(CONNECT_MSG);
                    ip = inputIP.getText().toString();

                    ServerConnect serverConnect = new ServerConnect();
                    serverConnect.start();


                    //Button startCameraButton = findViewById(R.id.button_start_camera);
                    startCameraButton.setVisibility(View.VISIBLE);
                    ServerButton.setVisibility(View.GONE);
                    inputIP.setVisibility(View.GONE);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.pause();
        }
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (STOP_MSG != "stop") {
            builder.setTitle("안내");
            builder.setMessage("대기상태로 돌아가시겠습니까?");

            builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    STOP_MSG = "stop";
                    stopCurrentPipeline();

                    TextView resultText0 = (TextView)findViewById(R.id.resultText0);
                    TextView resultText1 = (TextView)findViewById(R.id.resultText1);
                    EditText inputIP = (EditText)findViewById(R.id.inputIP);
                    Button startCameraButton = findViewById(R.id.button_start_camera);
                    Button ServerButton = findViewById(R.id.button_connect);

                    resultText0.setText("- timer -");
                    resultText1.setText("- sentence here -");
                    inputIP.setVisibility(View.VISIBLE);
                    startCameraButton.setVisibility(View.GONE);
                    ServerButton.setVisibility(View.VISIBLE);
                }
            });
            builder.setNeutralButton("아니오", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {}
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
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

    /** 서버 연결 */
    class ServerConnect extends Thread {
        public void run() {
            try {
                STOP_MSG = "";
                client = new Socket(ip, port);
                dataOutput = new DataOutputStream(client.getOutputStream());
                dataInput = new DataInputStream(client.getInputStream());
                String output_message = "Hello#";
                dataOutput.writeBytes(output_message);
                Thread.sleep(100);

                DataReceive receive = new DataReceive();
                DataSend send = new DataSend();
                receive.start();
                send.start();

            } catch (Exception e) {
                Log.w("??", e);
            }
        }
    }
    class DataReceive extends Thread {
        String output_message;

        public void run() {
            while (STOP_MSG != "stop") {
                if (stackedData[0] != null) {
                    try {
                        // 데이터 보내기
                        output_message = "";
                        for (int i = 0; i < dataSize; i++) {
                            output_message += stackedData[i] + "/";
                        }
                        output_message += stackedData[dataSize] + "#";
                        stackedData = new String[dataSize + 1];

                        dataOutput.writeBytes(output_message);
                        Thread.sleep(2);

                    } catch (Exception e) {
                        Log.w("????", e);
                    }
                }
            }
            try {
                output_message = "stop#";
                dataOutput.writeBytes(output_message);
            } catch (Exception e) {
                Log.w("???", e);
            }
        }
    }
    class DataSend extends Thread {
        String input_message;

        public void run() {
            while (STOP_MSG != "stop"){
                if (stackedData[0] != null) {
                    try {
                        //데이터 받기
                        byte[] buf;
                        int read_Byte;
                        buf = new byte[bufferSize];
                        read_Byte  = dataInput.read(buf);
                        input_message = new String(buf, 0, read_Byte);
                        String temp = input_message.intern();

                        while (temp != "endprocess") {
                            SentenceUpdate(input_message);
                            Thread.sleep(2);

                            buf = new byte[bufferSize];
                            read_Byte  = dataInput.read(buf);
                            input_message = new String(buf, 0, read_Byte);
                        }
                        Thread.sleep(2);

                    } catch (Exception e) {
                        Log.w("????", e);
                    }
                }
            }
        }
    }

    // 문장 업데이트
    private void SentenceUpdate(String recieveData){
        // 기존 단어와 다른 단어가 인식되었을 때
        String this_action = recieveData;
        if (word != this_action) {
            // 일단 문장에 추가
            sentence[sentence_index] = this_action;
            sentence_index++;

            // 단어간 인식 텀이 너무 길면 문장 초기화
            if (System.currentTimeMillis()-term > 6000) {
                sentence = new String[] {"","","","","","","","","","","","","","","","","","","",""};
                sentence[0] = this_action;
                sentence_index = 1;

                // 새 단어가 충분한 시간동안 인식되었으면 통과
            } else if (System.currentTimeMillis()-term > 2000) {
                // 이전 단어가 "?" 였다면 삭제
                if (sentence[sentence_index-2] == "?") {
                    sentence[sentence_index-2] = sentence[sentence_index-1];
                    sentence[sentence_index-1] = "";
                    sentence_index--;
                } else {
                    TTS(sentence[sentence_index-2]);
                }

                // 너무 짧은 시간동안 인식 변동이 있었다면 삭제
            } else {
                sentence[sentence_index-2] = sentence[sentence_index-1];
                sentence[sentence_index-1] = "";
                sentence_index--;
            }

            // 단어 및 시간 초기화
            word = this_action;
            term = System.currentTimeMillis();
        }
    }

    // TTS
    private void TTS(String inputText) {
        tts.setPitch((float)0.8); // 음성 톤 높이
        tts.setSpeechRate((float)1.0); // 음성 속도

        //QUEUE_FLUSH - 진행중인 출력을 끊고 출력
        //QUEUE_ADD - 진행중인 출력이 끝난 후에 출력
        tts.speak(inputText, TextToSpeech.QUEUE_ADD, null);
    }

    /** Sets up the UI components for the live demo with camera input. */
    private void setupLiveDemoUiComponents() {
        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setOnClickListener(
                v -> {
                    startCameraButton.setVisibility(View.GONE);
                    //stopCurrentPipeline();
                    try {
                        setupStreamingModePipeline(InputSource.CAMERA);
                    } catch (Exception e) {
                        TextView resultText0 = (TextView)findViewById(R.id.resultText0);
                        resultText0.setText(e.getMessage());
                    }
                });
    }

    /** Sets up core workflow for streaming mode. */
    private void setupStreamingModePipeline(InputSource inputSource) {

        this.inputSource = inputSource;
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(false)
                                .setMaxNumHands(2)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        if (inputSource == InputSource.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        } else if (inputSource == InputSource.VIDEO) {
            videoInput = new VideoInput(this);
            videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        }

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView = new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);
        hands.setResultListener(
                handsResult -> {
                    logWristLandmark(handsResult, /*showPixelValues=*/ false);

                    signDetectData(handsResult);

                    glSurfaceView.setRenderData(handsResult);
                    glSurfaceView.requestRender();
                });

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void startCamera() {
        cameraInput.start(
                this,
                hands.getGlContext(),
                CameraInput.CameraFacing.FRONT,
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (videoInput != null) {
            videoInput.setNewFrameListener(null);
            videoInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }

    private void logWristLandmark(HandsResult result, boolean showPixelValues) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        NormalizedLandmark wristLandmark =
                result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            int width = result.inputBitmap().getWidth();
            int height = result.inputBitmap().getHeight();
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                            wristLandmark.getX() * width, wristLandmark.getY() * height));
        } else {
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                            wristLandmark.getX(), wristLandmark.getY()));
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return;
        }
        Landmark wristWorldLandmark =
                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        Log.i(
                TAG,
                String.format(
                        "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                                + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                        wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
    }


    // 수어 인식
    private void signDetectData(HandsResult result) {

        TextView resultText0 = (TextView)findViewById(R.id.resultText0);
        TextView resultText1 = (TextView)findViewById(R.id.resultText1);
        String printText = "";
        for (int t=0; t<20; t++) {
            if (sentence[t] == "") {
                break;
            } else {
                printText = printText + " " + sentence[t];
            }
        }
        resultText0.setText(Float.toString(Math.round((System.currentTimeMillis() - term)/100)/10.0f) + "s");
        resultText1.setText(printText);

        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }

        // 한손씩 계산
        for (int n=0; n < result.multiHandLandmarks().size(); n++) {
            // 조인트 데이터 전처리
            List<NormalizedLandmark> joint = result.multiHandLandmarks().get(n).getLandmarkList();

            String data = "";
            // 조인트 좌표와 각도 평탄화
            for (int k=0; k<20; k++) {
                data += joint.get(k).getX() + ",";
                data += joint.get(k).getY() + ",";
                data += joint.get(k).getZ() + ",";
            }
            data += joint.get(20).getX() + ",";
            data += joint.get(20).getY() + ",";
            data += joint.get(20).getZ();
            // 데이터 축적
            for (int k=0; k<dataSize; k++) {
                stackedData[k] = stackedData[k+1];
            }
            stackedData[dataSize] = data;
        }
    }
}