package com.example.sleep_app;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.pedro.library.AutoPermissions;
import com.pedro.library.AutoPermissionsListener;


import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;
import com.zolad.zoominimageview.ZoomInImageView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements AutoPermissionsListener{
    Button button1, button2;
    ZoomInImageView zoomInImageView;
    private static final int REQUEST_CODE1 = 101;
    private static final int REQUEST_CODE2 = 102;
    private static Bitmap bitmap = null;
    private static Bitmap tempBitmap;
    private static Paint paint;
    private static Canvas canvas;

    MediaRecorder recorder1; //녹음 전 데시벨 측정용 (저장X)
    MediaRecorder recorder2; //녹음용

    String fileName;
    static boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);
        zoomInImageView = findViewById(R.id.imageView);

        AutoPermissions.Companion.loadAllPermissions(this, 103);

        File file = new File(Environment.getExternalStorageDirectory(),"-");
        fileName = file.getAbsolutePath();  // 파일 위치 가져오기
        Toast.makeText(getApplicationContext(), "파일 위치:"+fileName, Toast.LENGTH_SHORT).show();

        class NewRunnable implements Runnable{
            @Override
            public void run() {

                running = true; //실행중

                while (running) {
                    startRecording1();
                    double dB = 0; // 데시벨 초기화

                    while (dB < 50 && running == true) {
                        dB = getAmplitude1(); //데시벨 50 넘거나 종료버튼 눌리면 통과
                    }
                    stopRecording1(); //데시벨 측정 위한 record 멈추고

                    if (running == true) {
                        startRecording2();  // record2 - 실제 녹음 시작
                        long startTime = System.currentTimeMillis();

                        while (((System.currentTimeMillis() - startTime) < 5000) && (running ==true)) { // 5초동안 데시벨 50 미만 유지되면 통과
                            if (getAmplitude2() >= 50)
                                startTime = System.currentTimeMillis(); //데시벨 50 이상 감지되면 다시 5초 카운트
                        }
                        stopRecording2(); //record2 - 녹음 종료
                    }
                }
            }
        }


        Button button3 = findViewById(R.id.button3);
        button3.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                NewRunnable nr = new NewRunnable();
                Thread t = new Thread(nr);
                t.start(); // 데시벨 체크 & 녹음
                Toast.makeText(getApplicationContext(), "시작", Toast.LENGTH_SHORT).show();
            }
        });

        Button button4 = findViewById(R.id.button4);
        button4.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                running = false; // 데시벨 체크 & 녹음 종료

                //종료 확인
                while(recorder1 != null || recorder2 != null ) {}
                Toast.makeText(getApplicationContext(), "종료", Toast.LENGTH_SHORT).show();
            }
        });
    }



    public void selectImage(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE1);
        }
    }

    public void processCapture(View view) {
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE2);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE1) {
            zoomInImageView.setImageURI(data.getData());
            paint = new Paint();


            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(4);
            paint.setStyle(Paint.Style.STROKE);

            BitmapDrawable bitmapDrawable = (BitmapDrawable) zoomInImageView.getDrawable();
            bitmap = bitmapDrawable.getBitmap();
            //temp bitmap

            tempBitmap = bitmap.copy(bitmap.getConfig(), true);

            canvas = new Canvas(tempBitmap);
            processImage();


        }
        if (requestCode == REQUEST_CODE2) {
            Bundle bundle = data.getExtras();
            bitmap = (Bitmap) bundle.get("data");
            zoomInImageView.setImageBitmap(bitmap);
            paint = new Paint();


            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(4);
            paint.setStyle(Paint.Style.STROKE);
            //temp bitmap

            tempBitmap = bitmap.copy(bitmap.getConfig(), true);
            canvas = new Canvas(tempBitmap);
            processImage();


        }
    }

    private void processImage() {
        FaceDetector faceDetector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .build();

        if (!faceDetector.isOperational()) {
            Toast.makeText(getApplicationContext(), "Not Functional", Toast.LENGTH_LONG).show();
            return;
        }
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();

        SparseArray<Face> faceSparseArray = faceDetector.detect(frame);


        for (int i=0; i < faceSparseArray.size(); i++) {
            Face face = faceSparseArray.valueAt(i);

            float x1, y1, x2, y2;
            x1 = face.getPosition().x;
            y1 = face.getPosition().y;
            x2 = x1 + face.getWidth();
            y2 = y1 + face.getHeight();

            RectF rectF = new RectF(x1, y1, x2, y2);

            canvas.drawRoundRect(rectF, 2, 2, paint);

            for (Landmark landmark : face.getLandmarks()) {
                int cx, cy;
                cx = (int) landmark.getPosition().x;
                cy = (int) landmark.getPosition().y;

                paint.setColor(Color.GREEN);
                if (faceSparseArray.size() > 1) {
                    canvas.drawCircle(cx, cy, faceSparseArray.size() / 2,paint);
                } else {
                    canvas.drawCircle(cx, cy, faceSparseArray.size() / 2, paint);
                }

            }
        }
        zoomInImageView.setImageDrawable(new BitmapDrawable(getResources(),tempBitmap));
        int face_detected=faceSparseArray.size();
        Toast.makeText(getApplicationContext(),"No. of Faces Detected: "+face_detected+"",Toast.LENGTH_SHORT).show();

    }

    public void startRecording1(){
        if (recorder1 == null){
            recorder1 = new MediaRecorder();
        }
        recorder1.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder1.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder1.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder1.setOutputFile("/dev/null");

        try {
            recorder1.prepare();
            recorder1.start();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void stopRecording1(){
        if (recorder1 == null) {
            return;
        }
        recorder1.stop();
        recorder1.release();
        recorder1 = null;
    }

    //데시벨 측정
    public double getAmplitude1() {
        if (recorder1 != null)
            //return  (recorder.getMaxAmplitude()/2700.0);
            return   20 * Math.log10(recorder1.getMaxAmplitude() / 16.0);
        else
            return 0;
    }

    public double getAmplitude2() {
        if (recorder2 != null)
            //return  (recorder.getMaxAmplitude()/2700.0);
            return   20 * Math.log10(recorder2.getMaxAmplitude() / 16.0);
        else
            return 0;
    }

    public void startRecording2(){
        if (recorder2 == null){
            recorder2 = new MediaRecorder();
        }
        recorder2.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder2.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder2.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder2.setOutputFile( fileName + getTime() + ".mp4");

        try {
            recorder2.prepare();
            recorder2.start();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void stopRecording2(){
        if (recorder2 == null) {
            return;
        }
        recorder2.stop();
        recorder2.release();
        recorder2 = null;
    }

    //현재 날짜-시각 : for fileName
    private String getTime() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String getTime = dateFormat.format(date); return getTime;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AutoPermissions.Companion.parsePermissions(this, requestCode, permissions, this);
    }

    @Override
    public void onDenied(int i, String[] permissions) {
        Toast.makeText(this, "permissions denied: " + permissions.length, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onGranted(int i, String[] permissions) {
        Toast.makeText(this, "permissions granted: " + permissions.length, Toast.LENGTH_LONG).show();
    }

}