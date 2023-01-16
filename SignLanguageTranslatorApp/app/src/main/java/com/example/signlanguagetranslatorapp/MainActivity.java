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
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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

    Interpreter model;
    String[] actions = new String[] {
            "1시","2시","3시","4시","5시","6시","7시","8시","9시","10시","11시","12시",
            "오후","오전","만나다","내일","오늘","좋아","싫어","나","바쁘다","카페","안돼"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();
        setupLiveDemoUiComponents();
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

                    signDetect(handsResult);

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
    private void signDetect(HandsResult result) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        TextView resultText = (TextView)findViewById(R.id.resultText);

        // 한손씩 계산
        for (int n=0; n < result.multiHandLandmarks().size(); n++) {
            // 조인트 데이터 전처리
            /*
            List<NormalizedLandmark> joint = result.multiHandLandmarks().get(n).getLandmarkList();

            float[][] j1 = new float[20][3];
            float[][] j2 = new float[20][3];
            float[][] v = new float[21][3];
            float[] angle = new float[16];
            float[] d = new float[82];


            // double타입 어레이로 변환
            for (int i=1; i < 20; i++) {
                j1[i][0] = joint.get(i).getX();
                j1[i][1] = joint.get(i).getY();
                j1[i][2] = joint.get(i).getZ();
            }
            for (int i=0; i < 19; i++) {
                j2[i][0] = joint.get(i).getX();
                j2[i][1] = joint.get(i).getY();
                j2[i][2] = joint.get(i).getZ();
            }
            j2[4] = j2[0];
            j2[8] = j2[0];
            j2[12] = j2[0];
            j2[16] = j2[0];

            // 각 조인트의 벡터 구하기
            v[0][0] = j2[0][0] - (float)0.5;
            v[0][1] = j2[0][1] - (float)0.5;
            v[0][2] = j2[0][2] - (float)0;
            for (int j=0; j<20; j++) {
                v[j+1][0] = j1[j][0] - j2[j][0];
                v[j+1][1] = j1[j][1] - j2[j][1];
                v[j+1][2] = j1[j][2] - j2[j][2];
            }

            // 내적 구하고 아크코사인
            int temp = 0;
            for (int j=0; j<20; j++) {
                if (j==4 || j==8 || j==12 || j==16) {
                    temp++;
                }else{
                    angle[j-temp] = v[j][0] * v[j + 1][0] + v[j][1] * v[j + 1][1] + v[j][2] * v[j + 1][2];
                }
            }
            for (int k=0; k<16; k++) {
                angle[k] = (float) Math.toDegrees(Math.acos(angle[k]));
            }

            // 조인트 좌표와 각도 평탄화
            for (int k=0; k<21; k++) {
                d[k*3] = joint.get(k).getX();
                d[k*3+1] = joint.get(k).getY();
                d[k*3+2] = joint.get(k).getZ();
            }
            d[63] = (float) 0.5;
            d[64] = (float) 0.5;
            d[65] = (float) 0;
            for (int k=0; k<16; k++) {
                d[66+k] = angle[k];
            }
            */


            /*
            converted_model.tflite : 최초 변환 모델 (작동안됨)
            model_1.tflite : 옵션 끈거 / input:[82], output[23]
            model_2.tflite : 옵션 킨거 / input:[82], output[23]
            test.tflite : 등차수열 예측 / input:[3], output[1][1]
            */

            // 등차수열예측
            int[] input = new int[] {1,2,3};
            float[][] output = new float[1][1];
            Interpreter model = getTfliteInterpreter("test.tflite");
            try {
                model.run(input, output);
                resultText.setText(Float.toString(output[0][0]));
            }catch (Exception e){
                resultText.setText(e.getMessage());
            }

            // 우리거 예측
            /*
            float[] output = new float[23];
            Interpreter model = getTfliteInterpreter("model_2.tflite");
            try {
                model.run(d, output);
            }catch (Exception e){
                resultText.setText(e.getMessage());
            }
            */

            // 인식결과 출력
            /*
            int outputMaxIndex = 0;
            for (int l = 0; l < 28; l++) {
                if (output[outputMaxIndex] < output[l]) {
                    outputMaxIndex = l;
                }
            }
            resultText.setText(actions[outputMaxIndex]);
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