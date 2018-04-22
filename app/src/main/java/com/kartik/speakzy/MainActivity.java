package com.kartik.speakzy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.ibm.watson.developer_cloud.http.HttpMediaType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import omrecorder.AudioChunk;
import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;
import omrecorder.WriteAction;

import static android.provider.Telephony.Carriers.PASSWORD;
import static android.view.View.VISIBLE;
import static java.lang.Thread.sleep;

/**
 * This is a very bare sample app to demonstrate the usage of the CameraDetector object from Affectiva.
 * It displays statistics on frames per second, percentage of time a face was detected, and the user's smile score.
 *
 * The app shows off the maneuverability of the SDK by allowing the user to start and stop the SDK and also hide the camera SurfaceView.
 *
 * For use with SDK 2.02
 */
public class MainActivity extends Activity implements Detector.ImageListener, CameraDetector.CameraEventListener {

    final String LOG_TAG = "CameraDetectorDemo";
    int freq = 0;

    Button startSDKButton;
    Button surfaceViewVisibilityButton;
    TextView smileTextView;
    TextView ageTextView;
    TextView ethnicityTextView;
    ToggleButton toggleButton;

    SurfaceView cameraPreview;

    boolean isCameraBack = false;
    boolean isSDKStarted = false;

    RelativeLayout videoLayout;

    CameraDetector detector;

    int previewWidth = 0;
    int previewHeight = 0;

    int count = 5;
    ArrayList<Integer> freqList;
    int arrCount = 0;

    public static final int ANGER = 1;
    public static final int FEAR = 2;
    public static final int JOY = 3;
    public static final int SADNESS = 4;

    ImageView ivEmotion;

    //-----------------------------------AUDIO-------------------------------
    Recorder recorder;
    int mic = 0;
    int recording = 0;

    SharedPreferences preferences;
    public static final String CREATED_TIME = "created_time";
    String analyzeIntent;
    public static final String RECORDED_FILE_NAME = "_rec.wav";

    public static final String USERNAME = "a67343b7-384c-4569-b96a-cfa516c9e156";
    public static final String PASSWORD = "3FKRCwgKhsbo";

    TextView tvText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        smileTextView = (TextView) findViewById(R.id.smile_textview);
        ageTextView = (TextView) findViewById(R.id.age_textview);
        ethnicityTextView = (TextView) findViewById(R.id.ethnicity_textview);
        ivEmotion = (ImageView) findViewById(R.id.ivEmotion);
        tvText = (TextView) findViewById(R.id.tvText);

        toggleButton = (ToggleButton) findViewById(R.id.front_back_toggle_button);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isCameraBack = isChecked;
                switchCamera(isCameraBack? CameraDetector.CameraType.CAMERA_BACK : CameraDetector.CameraType.CAMERA_FRONT);
            }
        });

        startSDKButton = (Button) findViewById(R.id.sdk_start_button);
        startSDKButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSDKStarted) {
                    isSDKStarted = false;
                    stopDetector();
                    stopRecording();
                    startSDKButton.setText("Start Camera");
                } else {
                    isSDKStarted = true;
                    startDetector();
                    setupRecorder();
                    recorder.startRecording();
                    startSDKButton.setText("Stop Camera");
                }
            }
        });
        startSDKButton.setText("Start Camera");

        //We create a custom SurfaceView that resizes itself to match the aspect ratio of the incoming camera frames
        videoLayout = (RelativeLayout) findViewById(R.id.main_layout);
        cameraPreview = new SurfaceView(this) {
            @Override
            public void onMeasure(int widthSpec, int heightSpec) {
                int measureWidth = MeasureSpec.getSize(widthSpec);
                int measureHeight = MeasureSpec.getSize(heightSpec);
                int width;
                int height;
                if (previewHeight == 0 || previewWidth == 0) {
                    width = measureWidth;
                    height = measureHeight;
                } else {
                    float viewAspectRatio = (float)measureWidth/measureHeight;
                    float cameraPreviewAspectRatio = (float) previewWidth/previewHeight;

                    if (cameraPreviewAspectRatio > viewAspectRatio) {
                        width = measureWidth;
                        height =(int) (measureWidth / cameraPreviewAspectRatio);
                    } else {
                        width = (int) (measureHeight * cameraPreviewAspectRatio);
                        height = measureHeight;
                    }
                }
                setMeasuredDimension(width,height);
            }
        };
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1100);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP,RelativeLayout.TRUE);
        cameraPreview.setLayoutParams(params);
        videoLayout.addView(cameraPreview,0);

        surfaceViewVisibilityButton = (Button) findViewById(R.id.surfaceview_visibility_button);
        surfaceViewVisibilityButton.setText("HIDE SURFACE VIEW");
        surfaceViewVisibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraPreview.getVisibility() == VISIBLE) {
                    cameraPreview.setVisibility(View.INVISIBLE);
                    surfaceViewVisibilityButton.setText("SHOW SURFACE VIEW");
                } else {
                    cameraPreview.setVisibility(VISIBLE);
                    surfaceViewVisibilityButton.setText("HIDE SURFACE VIEW");
                }
            }
        });

        detector = new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT, cameraPreview);
        detector.setDetectSmile(true);
        detector.setDetectAllEmotions(true);
        detector.setDetectAllExpressions(true);
        detector.setDetectAge(true);
        detector.setDetectEthnicity(true);
        detector.setImageListener(this);
        detector.setOnCameraEventListener(this);
        freqList = new ArrayList<>();

        //-----------------------SPEECH-----------------------------
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String createdTime = preferences.getString(CREATED_TIME,"invalid");
        Log.d("LastAudioOnCreate", createdTime);
    }

    @Override
    protected void onResume() {
        super.onResume();

        freq = 0;
        if (isSDKStarted) {
            startDetector();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDetector();
    }

    void startDetector() {
        if (!detector.isRunning()) {
            detector.start();
        }
    }

    void stopDetector() {
        freq = 0;
        if (detector.isRunning()) {
            detector.stop();

            String avgEmotion = findAverageEmotion();
            Toast.makeText(this, "Average Emotion : " + avgEmotion, Toast.LENGTH_SHORT).show();
        }
    }

    private String findAverageEmotion() {
        String avgEmotion = "Some problem at the server end.";

        int emotionsFreq[] = new int[4];
        int anger = 0, fear = 0, sadness = 0, happy = 0;
        for(int i =0 ; i<freqList.size() ; i++){
            emotionsFreq[freqList.get(i)-1]++;
        }

        anger = emotionsFreq[0];
        fear = emotionsFreq[1];
        sadness = emotionsFreq[2];
        happy = emotionsFreq[3];

        if(anger>=fear && anger>=sadness && anger>=happy){
            avgEmotion = "Anger";
        }

        if(fear>=anger && fear>=sadness && fear>=happy){
            avgEmotion = "Fear";
        }

        if(sadness>=anger && sadness>=fear && sadness>=happy){
            avgEmotion = "Sadness";
        }

        if(happy>=fear && happy>=sadness && happy>=anger){
            avgEmotion = "Happy";
        }

        Log.d("TAG_AVG", "Anger : " + anger + " Fear : " + fear + " Sadness: "+ sadness + " Happy : " + happy);
        return avgEmotion;
    }

    void switchCamera(CameraDetector.CameraType type) {
        detector.setCameraType(type);
    }

    @Override
    public void onImageResults(List<Face> list, Frame frame, float v) {

        if (list == null)
            return;

        freq++;
        if(freq%count==0){
            Log.d("TAG_TOTAL",freq+"");

            if (list.size() == 0) {
                smileTextView.setText("NO FACE");
                ageTextView.setText("");
                ethnicityTextView.setText("");
            } else {
                Face face = list.get(0);
                smileTextView.setText(String.format("SMILE\n%.2f",face.expressions.getSmile()));
                switch (face.appearance.getAge()) {
                    case AGE_UNKNOWN:
                        ageTextView.setText("");
                        break;
                    case AGE_UNDER_18:
                        ageTextView.setText(R.string.age_under_18);
                        break;
                    case AGE_18_24:
                        ageTextView.setText(R.string.age_18_24);
                        break;
                    case AGE_25_34:
                        ageTextView.setText(R.string.age_25_34);
                        break;
                    case AGE_35_44:
                        ageTextView.setText(R.string.age_35_44);
                        break;
                    case AGE_45_54:
                        ageTextView.setText(R.string.age_45_54);
                        break;
                    case AGE_55_64:
                        ageTextView.setText(R.string.age_55_64);
                        break;
                    case AGE_65_PLUS:
                        ageTextView.setText(R.string.age_over_64);
                        break;
                }

                switch (face.appearance.getEthnicity()) {
                    case UNKNOWN:
                        ethnicityTextView.setText("");
                        break;
                    case CAUCASIAN:
                        ethnicityTextView.setText(R.string.ethnicity_caucasian);
                        break;
                    case BLACK_AFRICAN:
                        ethnicityTextView.setText(R.string.ethnicity_black_african);
                        break;
                    case EAST_ASIAN:
                        ethnicityTextView.setText(R.string.ethnicity_east_asian);
                        break;
                    case SOUTH_ASIAN:
                        ethnicityTextView.setText(R.string.ethnicity_south_asian);
                        break;
                    case HISPANIC:
                        ethnicityTextView.setText(R.string.ethnicity_hispanic);
                        break;
                }

                float angerPerc = face.emotions.getAnger();
                float fearPerc = face.emotions.getFear();
                float joyPerc = face.emotions.getJoy();
                float sadnessPerc = face.emotions.getSadness();

                if(angerPerc>fearPerc && angerPerc>joyPerc && angerPerc>sadnessPerc){
                    ivEmotion.setImageResource(R.drawable.angry);
                    freqList.add(1);
                }

                else if(fearPerc>angerPerc && fearPerc>joyPerc && fearPerc>sadnessPerc){
                    ivEmotion.setImageResource(R.drawable.fear);
                    freqList.add(2);
                }

                else if(joyPerc>fearPerc && joyPerc>angerPerc && joyPerc>sadnessPerc){
                    ivEmotion.setImageResource(R.drawable.happy);
                    freqList.add(3);
                }

                else if(sadnessPerc>fearPerc&&sadnessPerc>angerPerc&&sadnessPerc>joyPerc){
                    ivEmotion.setImageResource(R.drawable.sad);
                    freqList.add(4);
                }
            }
        }

    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    public void onCameraSizeSelected(int width, int height, Frame.ROTATE rotate) {
        if (rotate == Frame.ROTATE.BY_90_CCW || rotate == Frame.ROTATE.BY_90_CW) {
            previewWidth = height;
            previewHeight = width;
        } else {
            previewHeight = height;
            previewWidth = width;
        }
        cameraPreview.requestLayout();
    }


    //------------------------AUDIO-----------------------------------------

    private void setupRecorder() {

        new Runnable(){
            @Override
            public void run() {
                if(recorder!=null){
                    recorder = null;
                }
                recorder = OmRecorder.wav(
                        new PullTransport.Noise(mic(), new PullTransport.OnAudioChunkPulledListener() {
                            @Override public void onAudioChunkPulled(AudioChunk audioChunk) {

                            }
                        },new WriteAction.Default(),
                                new Recorder.OnSilenceListener() {
                                    @Override public void onSilence(long silenceTime) {
//                                        Log.e("silenceTime", String.valueOf(silenceTime));
//                                        Toast.makeText(StaticActivity.this, "silence of " + silenceTime + " detected",
//                                                Toast.LENGTH_SHORT).show();
                                    }
                                }, 200), recordedFile());
            }
        }.run();

    }

    private PullableSource mic() {
        return new PullableSource.NoiseSuppressor(
                new PullableSource.Default(
                        new AudioRecordConfig.Default(
                                MediaRecorder.AudioSource.MIC, AudioFormat.ENCODING_PCM_16BIT,
                                AudioFormat.CHANNEL_IN_MONO, 8000
                        )
                )
        );
    }

    private File recordedFile() {
        Date createdTimeWithSpaces = new Date();
        String createdTime = "";
        int count = 0;
        for(int i=0;i<(createdTimeWithSpaces.toString()).length();i++){
            if(count==14){
                break;
            }
            char curr = createdTimeWithSpaces.toString().charAt(i);
            if(curr!=' '&&curr!=':'&&curr!='+'){
                createdTime+=curr;
                count++;
            }
        }
        String fileLocation = String.valueOf(getExternalFilesDir(Environment.DIRECTORY_MUSIC));
        String filePath = fileLocation+"/" + createdTime + RECORDED_FILE_NAME;

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(CREATED_TIME,createdTime.toString());
        editor.commit();
//        preferences.edit().putString(CREATED_TIME,createdTime.toString());
        String createdTimeString = preferences.getString(CREATED_TIME,"invalid");
        Log.d("LastAudioWhenRecorded", createdTimeString);
        return new File(filePath);
    }

    private void stopRecording(){
        try {
            if(recorder!=null) {
                recorder.stopRecording();
                recording = 0;
                final String currentPath = recentFile();
                MediaPlayer mediaPlayer = new MediaPlayer();
                final double[] duration = new double[1];
                mediaPlayer.setDataSource(currentPath);
                mediaPlayer.prepare();
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
//                        duration[0] = mediaPlayer.getDuration()/1000.0;
//                        if(duration[0] >10) {
//                            Toast.makeText(MainActivity.this, "Audio Saved. Ready to Analyze", Toast.LENGTH_SHORT).show();
//                        }
//                        else{
//                            Toast.makeText(MainActivity.this, "Audio file should be of more than 10 seconds. File deleted.",
//                                    Toast.LENGTH_SHORT).show();
//                            File file = new File(currentPath);
//                            file.delete();
//                        }
                    }
                });
            }

            Toast.makeText(this, "All good", Toast.LENGTH_SHORT).show();
            generateSpokenText();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private String recentFile() {

        String createdTimeWithSpaces = preferences.getString(CREATED_TIME,"invalid"); //check to give toast.
        String createdTime = "";
        int count = 0;
        for(int i=0;i<createdTimeWithSpaces.length();i++){
            if(count==14){
                break;
            }
            char curr = createdTimeWithSpaces.charAt(i);
            if(curr!=' '&&curr!=':'&&curr!='+'){
                createdTime+=curr;
                count++;
            }
        }
        String fileLocation = String.valueOf(getExternalFilesDir(Environment.DIRECTORY_MUSIC));
        String filePath = fileLocation+"/"+createdTime+RECORDED_FILE_NAME;
//        String filePath = fileLocation+"/"+"audio1.wav";
        return filePath;
    }


    //-------------------------------------------------TEXT--------------------------------------
    private void generateSpokenText() {

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    SpeechToText service = new SpeechToText();
                    service.setUsernameAndPassword(USERNAME, PASSWORD);

                    File audio;
                    audio = new File(recentFile());

                    RecognizeOptions options = new RecognizeOptions.Builder()
                            .contentType(HttpMediaType.AUDIO_WAV).model("en-US_NarrowbandModel")
                            .build();

                    SpeechResults transcript = service.recognize(audio, options).execute();
                    Log.d("TAG123Transcript",transcript.toString());
                    final String text = getSpeechTextFromJson(transcript);
                    Log.d("TAG123Text",text);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("UIThreadText", text);
                            updateSpokenText(text);
                        }
                    });

                }
                catch (Exception e){
                    Log.d("TAG123Text",e+"");
                }

                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void updateSpokenText(final String text){
        try {
            tvText.setText(text);
        } catch (Exception e) {
            tvText.setText("Error. Please try again.");
        }
    }

    private String getSpeechTextFromJson(SpeechResults transcript) {

        try {
            String fullText = "";
            JSONObject transcriptObj = new JSONObject(transcript.toString());
            JSONArray results = transcriptObj.getJSONArray("results");

            for(int i=0;i<results.length();i++){
                JSONArray alternatives = ((JSONObject)results.get(i)).getJSONArray("alternatives");
                fullText += ((JSONObject)alternatives.get(0)).getString("transcript");
            }

            return fullText;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "Error loading.";
    }
}
