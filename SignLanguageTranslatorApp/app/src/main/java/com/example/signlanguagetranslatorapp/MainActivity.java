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
import android.content.Context;
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

import org.tensorflow.lite.Interpreter;

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
    private String ip = "211.106.58.229";
    private int port = 8080;
    private int bufferSize = 1024;
    private static String CONNECT_MSG = "connect";
    private static String STOP_MSG = "stop";

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

        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setVisibility(View.GONE);

        setServerConnection();
        //setupLiveDemoUiComponents();
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

    /** 서버 연결 */
    private void setServerConnection() {
        Button ServerButton = findViewById(R.id.button_connect);
        ServerButton.setOnClickListener(
                v -> {
                    Connect connect = new Connect();
                    connect.execute(CONNECT_MSG);
                });

        //카메라 시작 대기상태로
        /*
        //Button ServerButton = findViewById(R.id.button_start_camera);
        //Button startCameraButton = findViewById(R.id.button_start_camera);
        ServerButton.setVisibility(View.GONE);
        startCameraButton.setVisibility(View.VISIBLE);
        setupLiveDemoUiComponents();
        */
    }
    private class Connect extends AsyncTask<String , String, Void> {
        String output_message;
        String input_message;

        @Override
        protected Void doInBackground(String[] strings) {
            try {
                client = new Socket(ip, port);
                dataOutput = new DataOutputStream(client.getOutputStream());
                dataInput = new DataInputStream(client.getInputStream());
                output_message = strings[0];
                output_message = "123.123,56,/789.787,34";
                dataOutput.writeBytes(output_message);

            } catch (Exception e) {
                Log.w("??", e);
            }

            while (true){
                try {
                    byte[] buf = new byte[bufferSize];
                    int read_Byte  = dataInput.read(buf);
                    input_message = new String(buf, 0, read_Byte);
                    if (!input_message.equals(STOP_MSG)){
                        publishProgress(input_message);
                    }
                    else{
                        break;
                    }
                    Thread.sleep(2);
                } catch (Exception e) {
                    Log.w("????", e);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String[] params) {
            TextView result2 = (TextView)findViewById(R.id.result2);
            TextView result3 = (EditText)findViewById(R.id.result3);
            result2.setText(""); // Clear the chat box
            result2.append("보낸 메세지: " + output_message );
            result3.setText(""); // Clear the chat box
            result3.append("받은 메세지: " + params[0]);
        }

        @Override
        protected void onCancelled() {
            // cancel() 메소드가 호출되었을때 즉,
            // 강제로 취소하라는 명령이 호출되었을 때
            // 스레드가 취소되기 전에 수행할 작업(메인 스레드)
            // super.onCancelled();
        }
    }


    /** Sets up the UI components for the live demo with camera input. */
    private void setupLiveDemoUiComponents() {
        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setOnClickListener(
                v -> {
                    if (inputSource == InputSource.CAMERA) {
                        return;
                    }
                    stopCurrentPipeline();
                    setupStreamingModePipeline(InputSource.CAMERA);
                    startCameraButton.setVisibility(View.GONE);
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

        TextView resultText = (TextView)findViewById(R.id.resultText);
        String printText = "";
        for (int t=0; t<20; t++) {
            if (sentence[t] == "") {
                break;
            } else {
                printText = printText + " " + sentence[t];
            }
        }
        resultText.setText(Float.toString(Math.round((System.currentTimeMillis() - term)/100)/10.0f) + printText);

        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }

        // 한손씩 계산
        for (int n=0; n < result.multiHandLandmarks().size(); n++) {
            // 조인트 데이터 전처리
            List<NormalizedLandmark> joint = result.multiHandLandmarks().get(n).getLandmarkList();

            String data = "";
            // 조인트 좌표와 각도 평탄화
            for (int k=0; k<21; k++) {
                data += joint.get(k).getX() + ",";
                data += joint.get(k).getY() + ",";
                data += joint.get(k).getZ() + ",";
                data += "0";
            }
            // 데이터 축적
            for (int k=0; k<dataSize; k++) {
                stackedData[k] = stackedData[k+1];
            }
            stackedData[dataSize] = data;

            /*
            float[][] j1 = new float[20][3];
            float[][] j2 = new float[20][3];
            float[][] v = new float[21][3];
            float[] angle = new float[16];
            float[] d = new float[104];


            // double타입 어레이로 변환
            //[[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20], :3]
            for (int i=1; i < 20; i++) {
                j1[i][0] = joint.get(i).getX();
                j1[i][1] = joint.get(i).getY();
                j1[i][2] = joint.get(i).getZ();
            }
            //[[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19], :3]
            for (int i=0; i < 19; i++) {
                j2[i][0] = joint.get(i).getX();
                j2[i][1] = joint.get(i).getY();
                j2[i][2] = joint.get(i).getZ();
            }
            //[[0,1,2,3,0,5,6,7,0,9,10,11,0,13,14,15,0,17,18,19], :3]
            j2[4] = j2[0];
            j2[8] = j2[0];
            j2[12] = j2[0];
            j2[16] = j2[0];

            // 각 조인트의 벡터 구하기
            v[0][0] = j2[0][0] - (float)0.5;
            v[0][1] = j2[0][1] - (float)0.5;
            v[0][2] = j2[0][2] - (float)-1;
            //[[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20], :3]
            //[[0,1,2,3,0,5,6,7,0,9 ,10,11,0 ,13,14,15,0 ,17,18,19], :3]
            for (int j=0; j<20; j++) {
                v[j+1][0] = j1[j][0] - j2[j][0];
                v[j+1][1] = j1[j][1] - j2[j][1];
                v[j+1][2] = j1[j][2] - j2[j][2];
            }

            // 내적 구하고 아크코사인
            //[[0,1,2,3,-,5,6,7,-,9 ,10,11,-,13,14,15,-,17,18,19], :3]
            //[[1,2,3,4,-,6,7,8,-,10,11,12,-,14,15,16,-,18,19,20], :3]
            int temp = 0;
            for (int j=0; j<20; j++) {
                if (j==4 || j==8 || j==12 || j==16) {
                    temp++;
                }else{
                    angle[j-temp] =
                            v[j][0] * v[j + 1][0] +
                            v[j][1] * v[j + 1][1] +
                            v[j][2] * v[j + 1][2];
                }
            }
            for (int k=0; k<16; k++) {
                angle[k] = (float) Math.toDegrees(Math.acos(angle[k]));
            }

            // 조인트 좌표와 각도 평탄화
            for (int k=0; k<21; k++) {
                d[k*4] = joint.get(k).getX();
                d[k*4+1] = joint.get(k).getY();
                d[k*4+2] = joint.get(k).getZ();
                d[k*4+3] = 0;
            }
            d[84] = (float) 0.5;
            d[85] = (float) 0.5;
            d[86] = (float) -1;
            for (int k=0; k<16; k++) {
                d[87+k] = angle[k];
            }

            // 데이터 축적
            for (int k=0; k<9; k++) {
                input[0][k] = input[0][k+1].clone();
            }
            input[0][9] = d;


            //10개 모였는지 체크
            if (input[0][0][86] != -1) {
                continue;
            }

            float[][] output = new float[1][27];
            Interpreter tflite = getTfliteInterpreter("model_mb1.tflite");
            try {
                tflite.run(input, output);

                // 출력
                TextView result1 = (TextView)findViewById(R.id.result1);
                TextView result2 = (TextView)findViewById(R.id.result2);
                TextView result3 = (EditText)findViewById(R.id.result3);
                String outputText = "";

                for (int wtf=0; wtf<27; wtf++) {
                    outputText = outputText + "  " + actions[wtf] + ":";
                    outputText += Float.toString(Math.round(output[0][wtf]*100)/100.0f);
                }
                result1.setText(outputText);

                outputText = action_seq[0]+ "/" +action_seq[1]+ "/" +action_seq[2];
                result2.setText(outputText);


                // 데이타 평준화
                String Tempresult = "[";
                for (int wtf=0; wtf<104; wtf++) {
                    Tempresult += String.valueOf(d[wtf]);
                    Tempresult += "/";
                }
                Tempresult += "]";
                result3.setText(Tempresult);


                // 제일 유사한 수어 확인
                int outputMaxIndex = 0;
                for (int l = 0; l < 27; l++) {
                    if (output[0][outputMaxIndex] < output[0][l]) {
                        outputMaxIndex = l;
                    }
                }
                // 분석 결과 스택
                if (output[0][outputMaxIndex] > 0.9) {
                    action_seq[0] = action_seq[1];
                    action_seq[1] = action_seq[2];
                    action_seq[2] = actions[outputMaxIndex];

                    // 결과 검증 | 연속적으로 3회동안 같은 결과가 나왔는가?
                    String this_action = "?";
                    if (action_seq[0] == action_seq[1] && action_seq[1] == action_seq[2]) {
                        this_action = actions[outputMaxIndex];
                    }

                    // 기존 단어와 다른 단어가 인식되었을 때

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
                            }

                        // 너무 짧은 시간동안 인식 변동이 있었다면 삭제
                        } else {
                            sentence[sentence_index-2] = sentence[sentence_index-1];
                            sentence[sentence_index-1] = "";
                            sentence_index--;
                        }

                        // 단어 및 시간 초기화
                        word = "?";
                        term = System.currentTimeMillis();
                    }
                }
                tflite.close();

            }catch (Exception e){
                resultText.setText(e.getMessage());
            }
            */

        }
    }

    // 학습모델 전처리
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declareLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declareLength);
    }

    // 학습모델 로드
    private Interpreter getTfliteInterpreter(String modelPath) {
        try{
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}