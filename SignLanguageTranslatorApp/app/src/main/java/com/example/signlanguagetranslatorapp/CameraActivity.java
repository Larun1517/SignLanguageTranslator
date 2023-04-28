package com.example.signlanguagetranslatorapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CameraActivity extends AppCompatActivity {

    // mediapipe
    private Hands hands;
    private static final String TAG = "CameraActivity";
    private static final boolean RUN_ON_GPU = true;
    private enum InputSource { UNKNOWN, CAMERA }
    private InputSource inputSource = InputSource.UNKNOWN;
    private CameraInput cameraInput;
    private SolutionGlSurfaceView<HandsResult> glSurfaceView;
    private int cameraMode = 0; // 0-back, 1-front

    // sentence
    private String[] sentence = new String[] {"","","","","","","","","","","","","","","","","","","",""};
    private int sentence_index = 0;
    private String word = "?";
    private Long term = System.currentTimeMillis()-6000;
    private int termWord = 2000;
    private int termSentence = 6000;
    private static final int dataSize = 10 -1;
    private String[] stackedData = new String[dataSize+1];

    // tts
    private TextToSpeech tts;
    private Float ttsPitch = 0.8f;
    private Float ttsRate = 1.0f;

    // connection
    private Socket client;
    private DataOutputStream dataOutput;
    private DataInputStream dataInput;
    private String ip = "";
    private static final int port = 8080;
    private static final int bufferSize = 256;
    private static final String CONNECT_MSG = "connect";
    private String connect_status = ""; //stop


    @Override
    protected  void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        // 설정값 받아오기
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        ip = bundle.getString("ip","0.0.0.0");
        ttsPitch = (float) (bundle.getInt("ttsPitch", 8) / 10);
        ttsRate = (float) (bundle.getInt("ttsRate", 10) / 10);
        termWord = (int) (bundle.getDouble("termWord", 2) * 1000);
        termSentence = (int) (bundle.getDouble("termSentence", 6) * 1000);
        cameraMode = bundle.getInt("cameraMode", 1);

        // tts 정의
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREA);
            }
        });

        // stt 다이얼로그
        Button Setting = findViewById(R.id.button_sttDialog);
        Setting.setOnClickListener( v -> {
            STTDialog sttDialog = new STTDialog(CameraActivity.this);
            sttDialog.setCancelable(true);
            sttDialog.getWindow().setGravity(Gravity.CENTER);
            sttDialog.show();
        });


        setupSequence();
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
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        }
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("안내");
        builder.setMessage("대기상태로 돌아가시겠습니까?");

        builder.setPositiveButton("예", (dialog, which) -> {
            connect_status = "stop";
            stopCurrentPipeline();
            finish();
        });
        builder.setNeutralButton("아니오", (dialog, which) -> {});

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /** 서버 및 미디어파이프 카메라 실행 */ // onCreate 밖으로 빼야 작동함
    private void setupSequence() {
        // 서버 연결
        connect_status = "connecting";
        ServerConnect serverConnect = new ServerConnect();
        serverConnect.start();
        //while (connect_status != "connected") {}

        // 미디어파이프 카메라 실행
        //stopCurrentPipeline();
        setupStreamingModePipeline();
    }

    // 서버 관련 스레드
    class ServerConnect extends Thread {
        public void run() {
            try {
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

                connect_status = "connected";

            } catch (Exception e) {
                Log.w("_ServerConnect_Error", e);
            }
        }
    }
    class DataReceive extends Thread {
        String output_message;

        public void run() {
            while (connect_status != "stop") {
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
                        Thread.sleep(10);

                    } catch (Exception e) {
                        Log.w("????", e);
                    }
                }
            }
            try {
                output_message = "stop#";
                dataOutput.writeBytes(output_message);
            } catch (Exception e) {
                Log.w("_DataReceive_Error", e);
            }
        }
    }
    class DataSend extends Thread {
        String input_message;

        public void run() {
            while (connect_status != "stop"){
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
                            Thread.sleep(10);

                            buf = new byte[bufferSize];
                            read_Byte  = dataInput.read(buf);
                            input_message = new String(buf, 0, read_Byte);
                        }
                        Thread.sleep(10);

                    } catch (Exception e) {
                        Log.w("_DataSend_Error", e);
                    }
                }
            }
        }
    }


    /** 문장 업데이트 */
    private void SentenceUpdate(String recieveData){
        // 기존 단어와 다른 단어가 인식되었을 때
        if (!Objects.equals(word, recieveData)) {
            // 일단 문장에 추가
            sentence[sentence_index] = recieveData;
            sentence_index++;

            // 단어간 인식 텀이 너무 길면 문장 초기화
            if (System.currentTimeMillis()-term > termSentence) {
                sentence = new String[] {"","","","","","","","","","","","","","","","","","","",""};
                sentence[0] = recieveData;
                sentence_index = 1;

                // 새 단어가 충분한 시간동안 인식되었으면 통과
            } else if (System.currentTimeMillis()-term > termWord) {
                // 이전 단어가 "?" 였다면 삭제
                //if (sentence[sentence_index-2] == "?") {
                if (sentence[sentence_index-2].equals("?")) {
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
            word = recieveData;
            term = System.currentTimeMillis();
        }
    }
    /** TTS */
    private void TTS(String inputText) {
        tts.setPitch(ttsPitch); // 음성 톤 높이
        tts.setSpeechRate(ttsRate); // 음성 속도

        //QUEUE_FLUSH - 진행중인 출력을 끊고 출력
        //QUEUE_ADD - 진행중인 출력이 끝난 후에 출력
        tts.speak(inputText, TextToSpeech.QUEUE_ADD, null);
    }


    /** Sets up the UI components for the live demo with camera input. */
    private void startCamera() {
        if (cameraMode == 0) {
            cameraInput.start(
                    this,
                    hands.getGlContext(),
                    CameraInput.CameraFacing.BACK,
                    glSurfaceView.getWidth(),
                    glSurfaceView.getHeight()
            );
        } else {
            cameraInput.start(
                    this,
                    hands.getGlContext(),
                    CameraInput.CameraFacing.FRONT,
                    glSurfaceView.getWidth(),
                    glSurfaceView.getHeight()
            );
        }
    }
    /** Sets up core workflow for streaming mode. */
    private void setupStreamingModePipeline() {

        this.inputSource = InputSource.CAMERA;
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

        cameraInput = new CameraInput(this);
        cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView = new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);
        hands.setResultListener(
            handsResult -> {
                signDetectData(handsResult);

                glSurfaceView.setRenderData(handsResult);
                glSurfaceView.requestRender();
            }
        );

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        glSurfaceView.post(this::startCamera);

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }
    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }


    /** 데이터 모으기 */
    private void signDetectData(HandsResult result) {

        TextView text_timer = (TextView)findViewById(R.id.text_timer);
        TextView text_sentence = (TextView)findViewById(R.id.text_sentence);
        String printText = "";
        for (int t=0; t<20; t++) {
            if (sentence[t] == "") {
                break;
            } else {
                printText = printText + " " + sentence[t];
            }
        }
        text_timer.setText(Math.round((System.currentTimeMillis() - term) / 100) / 10.0f + "s");
        text_sentence.setText(printText);

        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }

        // 한손씩 계산
        for (int n=0; n < result.multiHandLandmarks().size(); n++) {
            // 조인트 데이터 전처리
            List<LandmarkProto.NormalizedLandmark> joint = result.multiHandLandmarks().get(n).getLandmarkList();

            //왼손?
            //result.multiHandedness().get(n).getLabel().equals("Left");

            StringBuilder data = new StringBuilder();
            // 조인트 좌표와 각도 평탄화
            for (int k=0; k<20; k++) {
                data.append(joint.get(k).getX()).append(",");
                data.append(joint.get(k).getY()).append(",");
                data.append(joint.get(k).getZ()).append(",");
            }
            data.append(joint.get(20).getX()).append(",");
            data.append(joint.get(20).getY()).append(",");
            data.append(joint.get(20).getZ());
            // 데이터 축적
            for (int k=0; k<dataSize; k++) {
                stackedData[k] = stackedData[k+1];
            }
            stackedData[dataSize] = data.toString();
        }
    }
}