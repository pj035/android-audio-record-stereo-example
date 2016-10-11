package com.philipp.jaecks.android_audio_record_stereo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * Created by Philipp on 11.10.2016.
 */
public class AudioRecorder {
    private static final String TAG = "Recorder";

    private AudioRecord recorder;
    private HandlerThread recordThread;

    // no. of channels, sampleRate, sample size (in bits), buffer size, audio source, sample size
    private short   nChannels = 2;      // stereo since we want to access multiple mics
    private int     sampleRate = 44100; // is currently the only rate that is guaranteed to work on all devices
    private short   bSamples = 16;
    private int     bufferSize;
    private int     aChannels = AudioFormat.CHANNEL_IN_STEREO;
    private int     aSource = MediaRecorder.AudioSource.MIC;
    private int     aFormat = AudioFormat.ENCODING_PCM_16BIT;

    // Number of frames written to file on each output(only in uncompressed mode)
    private int     framePeriod;

    // Buffer for output(only in uncompressed mode)
    private byte[]  buffer;

    // Number of bytes written to file after header(only in uncompressed mode)
    // after stop() is called, this size is written to the header/data chunk in the wave file
    private int                      payloadSize;

    //    private boolean splitRecording = false;
    private boolean recording = false;

    private boolean splitChannelsIntoSeparateFiles = false;
    private RandomAccessFile randomAccessWriterChannelLeft;
    private RandomAccessFile randomAccessWriterChannelRight;

    private int noRecordings;

    public AudioRecorder() {
        this.noRecordings = 0;

        int recordLength = 500; // ms
        framePeriod = sampleRate * recordLength / 1000;
        bufferSize = framePeriod * 2 * bSamples * nChannels / 8;
        int audioBufferInit = AudioRecord.getMinBufferSize(sampleRate, nChannels, aFormat);
        Log.d(TAG, "Audiobuffer size is: " + audioBufferInit);
        Log.d(TAG, "Calculated buffer size is: " + bufferSize);
        Log.d(TAG, "FramePeriod is: " + framePeriod);
        if (bufferSize < audioBufferInit) {
            bufferSize = audioBufferInit;
            // update frame period
            framePeriod = bufferSize / (2 * bSamples * nChannels / 8);
            Log.w(TAG, "Increased buffer size to " + bufferSize);
        }
    }

    private boolean initRecorder() {
        Log.d(TAG, "Init recorder");
        recorder = new AudioRecord(aSource, sampleRate, aChannels, aFormat, bufferSize);

        int sessionId = recorder.getAudioSessionId();

        Log.d(TAG, "AutomaticGainControl available: " + AutomaticGainControl.isAvailable());
        Log.d(TAG, "AcousticEchoCanceler available: " + AcousticEchoCanceler.isAvailable());

//        if (AcousticEchoCanceler.isAvailable()) {
//            AcousticEchoCanceler.create(sessionId);
//            Log.d(TAG, "activated AEC");
//        }
//        if (AutomaticGainControl.isAvailable() ) {
//            AutomaticGainControl.create(sessionId);
//            Log.d(TAG, "activated AGC");
//        }

        if (recordThread == null) {
            recordThread = new HandlerThread("RecorderThread", Thread.MAX_PRIORITY);
            recordThread.start();
        }
        // getLooper will block until Looper has been initialized
        Handler handler = new Handler(recordThread.getLooper());

        recorder.setPositionNotificationPeriod(framePeriod);
        recorder.setRecordPositionUpdateListener(updateListener, handler);

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord could not be initialized");
            return false;
        }
        Log.d(TAG, "Recorder initialized");
        return true;
    }

    public void start(File wavDir, boolean splitChannelsIntoSeparateFiles) {
        if (recorder == null) {
            initRecorder();
        }
        this.splitChannelsIntoSeparateFiles = splitChannelsIntoSeparateFiles;

        String filePath = wavDir.getAbsolutePath() + "/sample_" + SystemClock.uptimeMillis() + "_channel_left.wav";
        randomAccessWriterChannelLeft = prepareFile(filePath, randomAccessWriterChannelLeft);

        if (splitChannelsIntoSeparateFiles) {
            filePath = wavDir.getAbsolutePath() + "/sample_" + SystemClock.uptimeMillis() + "_channel_right.wav";
            randomAccessWriterChannelRight = prepareFile(filePath, randomAccessWriterChannelRight);
        }

        payloadSize = 0;
        recording = true;
        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
                Log.d(TAG, "Start recorder");
                recorder.startRecording();
                recorder.read(buffer, 0, buffer.length);
            }
        }

    }

    public int stop() {
        recording = false;

        if (recorder != null) {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
                Log.d(TAG, "Recorder stopped");
            }
            recorder.release();
            recorder = null;
            // stop thread
//            recordThread.quitSafely();
            recordThread.interrupt();
//            recordThread = null;
            Log.d(TAG, "Recorder released");

            // is this correct?
            if (splitChannelsIntoSeparateFiles) {
                payloadSize /= nChannels;
            }

            // close left channel writer
            if (randomAccessWriterChannelLeft != null) {
                try {
                    Log.d(TAG, "append size information to file: " + payloadSize + " bytes");
                    randomAccessWriterChannelLeft.seek(4); // write size to RIFF header
                    randomAccessWriterChannelLeft.writeInt(Integer.reverseBytes(36 + payloadSize));
                    randomAccessWriterChannelLeft.seek(40);    // write size to Subchunk2Size field
                    randomAccessWriterChannelLeft.writeInt(Integer.reverseBytes(payloadSize));
                    randomAccessWriterChannelLeft.close();

                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
            // close right channel writer
            if (randomAccessWriterChannelRight != null) {
                try {
                    Log.d(TAG, "append size information to file: " + payloadSize + " bytes");
                    randomAccessWriterChannelRight.seek(4); // write size to RIFF header
                    randomAccessWriterChannelRight.writeInt(Integer.reverseBytes(36 + payloadSize));
                    randomAccessWriterChannelRight.seek(40);    // write size to Subchunk2Size field
                    randomAccessWriterChannelRight.writeInt(Integer.reverseBytes(payloadSize));
                    randomAccessWriterChannelRight.close();

                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }

        }
        return noRecordings;
    }

    public RandomAccessFile prepareFile(String pathToFile, RandomAccessFile file) {
        try {
            Log.d(TAG, "Prepare file at " + pathToFile);
            // write file header
            file = new RandomAccessFile(pathToFile, "rw");

            short localNChannels = 2;
            if (splitChannelsIntoSeparateFiles) {
                localNChannels = 1;
            }

            file.setLength(0);    // set file lentgh to 0, to prevent unexpected behavior in case the file already existed
            file.writeBytes("RIFF");
            file.writeInt(0); // final size not known yet, write 0
            file.writeBytes("WAVE");
            file.writeBytes("fmt ");
            file.writeInt(Integer.reverseBytes(16));  // sub chunk size, 16 for PCM
            file.writeShort(Short.reverseBytes((short)1));    // audioformat, 1 for PCM
            file.writeShort(Short.reverseBytes(localNChannels));   // number of channels, 1 for mono, 2 for stereo
            file.writeInt(Integer.reverseBytes(sampleRate));
            file.writeInt(Integer.reverseBytes(sampleRate*bSamples*localNChannels/8)); //byte rate, samplerate*numberOfChannels*BitsPerSample/8
            file.writeShort(Short.reverseBytes((short)(nChannels*bSamples/8))); // Block align; numverOfCahnnels*BitsPerSample /8
            file.writeShort(Short.reverseBytes(bSamples));    // bits per sample
            file.writeBytes("data");
            file.writeInt(0); // data chunk size not known yet, write 0

            buffer = new byte[framePeriod*bSamples/8*nChannels];
            Log.d(TAG, "WAVE file prepared; buffer length: " + buffer.length);
            return file;
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return null;
    }

    private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener() {
        @Override
        public void onMarkerReached(AudioRecord recorder) {
            // not used
        }

        @Override
        public void onPeriodicNotification(AudioRecord recorder) {

            if (recording) {
                recorder.read(buffer, 0, buffer.length); // Fill buffer
                Log.d(TAG, "try to write");
                try {
                    if (splitChannelsIntoSeparateFiles) {
                        //  according to http://stackoverflow.com/questions/20594750/split-two-channels-of-audiorecord-of-channel-in-stereo
                        // and http://stackoverflow.com/questions/15418267/how-to-access-the-second-mic-android-such-as-galaxy-3
                        // the bytes of the left channel have even indices (0, 2, 4,..)
                        // and the bytes of the right channel have odd  (1, 3, 5,..)
                        // some devices may have another order
                        byte[] leftData = new byte[buffer.length/2];
                        byte[] rightData = new byte[buffer.length/2];

                        for (int i = 0; i < buffer.length/2; i = i + 2) {
                            leftData[i] = buffer[2*i];
                            leftData[i+1] = buffer[2*i+1];

                            rightData[i] = buffer[2*i+2];
                            rightData[i+1] = buffer[2*i+3];
                        }
                        randomAccessWriterChannelLeft.write(leftData);
                        randomAccessWriterChannelRight.write(rightData);
                    } else {
                        randomAccessWriterChannelLeft.write(buffer);
                    }
                    payloadSize += buffer.length;
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }
    };

    public void cleanUp() {
        Log.d(TAG, "Clean up everything");
        if (recorder != null) {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        }
        if (recordThread != null) {
            recordThread.quitSafely();
            recordThread.interrupt();
            recordThread = null;
        }
    }
}
