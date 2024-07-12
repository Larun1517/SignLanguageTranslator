package com.example.signlanguagetranslatorapp;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MkLog extends Application {
    public String TAG = MkLog.class.getSimpleName();

    /**
     * Application 이 시작되기 전 MyApp 의 onCreate 실행
     */
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, " *** onCreate()");

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddhhmmss");
        String time = format.format(date);

        if ( isExternalStorageWritable() ) {
            //read, write 둘다 가능

            File appDirectory = new File( Environment.getExternalStorageDirectory()+"/MyApp" );
            File logDirectory = new File( appDirectory + "/logs" );
            Log.d(TAG, "*** onCreate() - appDirectory :: "+appDirectory.getAbsolutePath());
            Log.d(TAG, "*** onCreate() - logDirectory :: "+logDirectory.getAbsolutePath());

            //appDirectory 폴더 없을 시 생성
            if ( !appDirectory.exists() ) {
                appDirectory.mkdirs();
            }

            //logDirectory 폴더 없을 시 생성
            if ( !logDirectory.exists() ) {
                logDirectory.mkdirs();
            }

            File logFile = new File( logDirectory, "logcat_" + time + ".txt" );
            Log.d(TAG, "*** onCreate() - logFile :: "+logFile);

            //이전 logcat 을 지우고 파일에 새 로그을 씀
            try {
                Process process = Runtime.getRuntime().exec("logcat -c");
                process = Runtime.getRuntime().exec("logcat -f " + logFile);
            } catch ( IOException e ) {
                e.printStackTrace();
            }

        } else if ( isExternalStorageReadable() ) {
            //read 만 가능
        } else {
            //접근 불가능
        }
    }


    /**
     * 외부저장소 read/write 가능 여부 확인
     * @return
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if ( Environment.MEDIA_MOUNTED.equals( state ) ) {
            return true;
        }
        return false;
    }


    /**
     * 외부저장소 read 가능 여부 확인
     * @return
     */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if ( Environment.MEDIA_MOUNTED.equals( state ) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals( state ) ) {
            return true;
        }
        return false;
    }

}