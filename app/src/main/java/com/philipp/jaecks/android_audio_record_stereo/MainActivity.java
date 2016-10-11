package com.philipp.jaecks.android_audio_record_stereo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_PERMISSION_RECORD_AUDIO = 0;
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 1;

    private AudioRecorder audioRecorder;
    private File recordingDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ask for permissions
        int permissionWriteToStorage = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionWriteToStorage == PackageManager.PERMISSION_DENIED) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);
        } else {
            setUpWorkingDirectory();
        }

        int permissionAudio = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permissionAudio == PackageManager.PERMISSION_DENIED) {
            requestPermission(Manifest.permission.RECORD_AUDIO, REQUEST_PERMISSION_RECORD_AUDIO);
        } else {
            audioRecorder = new AudioRecorder();
        }
    }

    private void requestPermission(String permission, int permissionCode) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, permissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE:
                setUpWorkingDirectory();
                break;
            case REQUEST_PERMISSION_RECORD_AUDIO:
                if (audioRecorder == null) {
                    audioRecorder = new AudioRecorder();
                }
                break;
            default:
                break;
        }
    }

    /**
     * Checks permission to write to external storage and creates the working directory if possible
     */
    private void setUpWorkingDirectory() {
        int permissionStorageWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionStorageWrite == PackageManager.PERMISSION_DENIED) {
            sendToast("I don't have the permission to write something to the storage");
            return;
        }
        File externalStorage = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath());
        File root = new File(externalStorage.getAbsolutePath() + "/android_audio_record_stereo");


        // set up working directory
        if (root.mkdirs()) {
            Log.d(TAG, "Created root dir");
        }
        // create dir for sample data
        Date date = new Date();
        String day = (String) android.text.format.DateFormat.format("dd", date);
        String intMonth = (String) android.text.format.DateFormat.format("MM", date);
        recordingDir = new File(root.getAbsolutePath() + "/wav_samples_" + intMonth + "_" + day);
        if (recordingDir.mkdirs()) {
            Log.d(TAG, "Created root dir for recording files for this day");
        }
    }


    public void startRecording(View view) {

        // check audio permission
        int permissionAudioRecord = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permissionAudioRecord == PackageManager.PERMISSION_DENIED) {
            sendToast("I don't have the permission to access the microphone.");
            return;
        }

        File workingDir = new File(recordingDir.getAbsolutePath() + "/sample_" + SystemClock.elapsedRealtime());
        if (workingDir.mkdir()) {
            Log.d(TAG, "created working dir: " + workingDir.getAbsolutePath());
        }

        CheckBox chkbxSplitChannelFiles = (CheckBox)findViewById(R.id.chkbxSplitChannelFiles);
        boolean splitChannelFiles = chkbxSplitChannelFiles.isChecked();

        Log.d(TAG, "split channel files: " + splitChannelFiles);

        audioRecorder.start(workingDir, splitChannelFiles);

        toggleRecordButtons();
    }


    public void stopRecording(View view) {
        int recordings = audioRecorder.stop();

//        TextView txtRecording = (TextView) findViewById(R.id.txtRecorded);
//        txtRecording.setText("Recorded files: " + recordings);

        toggleRecordButtons();
    }


    private void toggleRecordButtons() {
        Button btnStart = (Button) findViewById(R.id.btnStartRecording);
        Button btnStop = (Button) findViewById(R.id.btnStopRecording);

        btnStart.setEnabled(!btnStart.isEnabled());
        btnStop.setEnabled(!btnStop.isEnabled());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioRecorder.stop();
        audioRecorder.cleanUp();
    }


    private void sendToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
