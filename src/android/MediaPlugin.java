package com.plugin;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.czt.mp3recorder.MP3Recorder;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by loop on 2015/11/17.
 */
public class MediaPlugin extends CordovaPlugin {

    private static final String TAG = MediaPlugin.class.getName();

    public static final String ACTION_START_RECORD = "START_RECORD";
    public static final String ACTION_STOP_RECORD = "STOP_RECORD";
    public static final String ACTION_STOP_SHOWMYWOOVIDEO = "showMywooVideo";
    public static final String ACTION_SHOW_SERVICE = "showMywooService";
    public static final String ACTION_CHECK_UPDATE = "checkUpdate";

    public static final String EVENT_TYPE_VOICE = "VOICE_UPDATE";
    public static final String RECORD_STATUS_RECORDING = "recording";
    public static final String RECORD_STATUS_END = "end";
    public static final String RECORD_STATUS_WAIT = "wait";
    public static final String RECORD_STATUS_NORECORD = "norecord";

    //录音相关的属性
    private boolean isGetVoiceRun = false;
    private boolean recordTrans = false;
    //    private Handler handler;
    private AudioRecord mAudioRecord;
    /**
     * 文件路径
     */
    private String rawPath = null;
    private String mp3Path = null;
//    private String wavPath = null;
    private static final String tempRecordFileName = "mwoo_tmp_audio";//临时录音文件名
    // 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    static final int SAMPLE_RATE_IN_HZ = 44100;
    static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ,
            AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
    private short[] buffer = new short[BUFFER_SIZE];

    private long lastSilenceTime = System.currentTimeMillis();//上一次开始静默的时间
    private static double minVolume = 40;//静默声音的阈值
    private long exitPeriod = 6000;//静默超时时间6000毫秒
    private long maxPeriod = 1000*60;//最长录音时间
    private long startTime = 0;//录音开始时间
    private  CallbackContext voiceCallback;
    private boolean recordValid = false;


    //新的录音工具
    private File record_mp3;
    private MP3Recorder mRecorder;
    private Handler handler;


//    private String currentToId;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        record_mp3 = new File(Environment.getExternalStorageDirectory(),tempRecordFileName+".mp3");
        mRecorder = new MP3Recorder(record_mp3);
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(msg.what){
                    case 1:
                        double volume = mRecorder.getVolume();

                        volume = volume/MP3Recorder.MAX_VOLUME;
                        Log.d(TAG, "volume:"+volume);
                        if(volume > 0.3){
                            lastSilenceTime = System.currentTimeMillis();
                            recordValid = true;
                        }
                        Log.d(TAG, "recordValid:"+recordValid);
                        updateVoice(volume,RECORD_STATUS_RECORDING);

                        long currTime = System.currentTimeMillis();
                        //如果静默超时，或超过最长录音时间限制，则退出录音
                        if((currTime - lastSilenceTime > exitPeriod) || (currTime - startTime >= maxPeriod)){
                            Log.d(TAG, "no voice and stop record...");
                            //结束录音
                            mRecorder.stop();
                            updateVoice(0.0, RECORD_STATUS_END);
                            handler.removeMessages(1);
                        }else if(mRecorder.isRecording()){
                            handler.sendEmptyMessageDelayed(1,200);
                        }


                        break;
                    case 2:
                        handler.removeMessages(1);
                        break;

                }

                return false;
            }
        });
    }

    @Override
    public void onDestroy() {

        if(isGetVoiceRun){
            isGetVoiceRun = false;
        }
        if(null != mAudioRecord){
            mAudioRecord.stop();
            mAudioRecord.release();
        }

        //释放
        mRecorder.stop();
        mRecorder.releaseRecord();
        super.onDestroy();
    }

    @Override
    public boolean execute(String action, final  JSONArray data, final CallbackContext callbackContext) throws JSONException {
//        return super.execute(action, args, callbackContext);
        if(ACTION_START_RECORD.equals(action)){
            //如果没有录音，则开始录音
            if(!recordTrans){

                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {

                        try {
                            mRecorder.start();
                            recordTrans = true;
                            recordValid = false;
                            startTime = System.currentTimeMillis();
                            lastSilenceTime = startTime;
                            Log.d(TAG,"开始录音...："+mRecorder.isRecording());
                            handler.sendEmptyMessage(1);
                        } catch (IOException e) {
                            Log.e(TAG,e.getMessage());
                            e.printStackTrace();
                        }

                    }
                });

                callbackContext.success("开始录音");
            }else{
                callbackContext.error("正在录音");
            }




        }else if(ACTION_STOP_RECORD.equals(action)){

            /*if(isGetVoiceRun){
                stopRecord();
            }*/

            handler.sendEmptyMessage(2);
            mRecorder.stop();
            updateVoice(0.0, RECORD_STATUS_END);


        }else if(EVENT_TYPE_VOICE.equals(action)){
            this.voiceCallback = callbackContext;
            Log.d(TAG,"准备获取音量:"+mRecorder.isRecording());
            if(mRecorder.isRecording()){
                updateVoice(0.0,RECORD_STATUS_WAIT);

            }
 




        }  
        return true;
    }

    /**
     * 更新音量大小
     * @param voice
     */
    private void updateVoice(Double voice,String type){
        JSONObject obj = new JSONObject();

        try {
            obj.put("VOICE",voice);
            obj.put("RECORDSTATUS",type);
            long recordTime = System.currentTimeMillis() - startTime;
            obj.put("RECORDTIME",recordTime);
            Log.d(TAG, "updateVoice,recordValid:"+recordValid);
            obj.put("RECORDVALID",recordValid);
            if(RECORD_STATUS_END.equals(type)){
                recordTrans = false;
                if(recordValid){
//                    Uri uri = Uri.fromFile(new File(mp3Path));
                    Uri uri = Uri.fromFile(record_mp3);
                    obj.put("RECORDFILE",uri.toString());
                }else{
                    obj.put("RECORDFILE","");
                }


            }



        } catch (JSONException e) {
            e.printStackTrace();
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK,obj);
        result.setKeepCallback(true);
        if(RECORD_STATUS_END.equals(type)){
            result.setKeepCallback(false);
        }else if(RECORD_STATUS_NORECORD.equals(type)){
            result.setKeepCallback(false);
        }

        this.voiceCallback.sendPluginResult(result);
        Log.d(TAG, "updateVoice:" + voice);
//        webView.postMessage(EVENT_TYPE_VOICE,voice);//给其他插件传递消息
    }


    /**
     * 更新音量大小
     * @param voice
     */
    private void updateVoice_old(Double voice,String type){
         JSONObject obj = new JSONObject();

        try {
            obj.put("VOICE",voice);
            obj.put("RECORDSTATUS",type);
            if(RECORD_STATUS_END.equals(type)){

                if(recordValid){
                    Uri uri = Uri.fromFile(new File(mp3Path));
                    obj.put("RECORDFILE",uri.toString());
                }else{
                    obj.put("RECORDFILE","");
                }

                long recordTime = System.currentTimeMillis() - startTime;
                obj.put("RECORDTIME",recordTime);
                obj.put("RECORDVALID",recordValid);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK,obj);
        result.setKeepCallback(true);
        if(RECORD_STATUS_END.equals(type)){
            result.setKeepCallback(false);
            stopRecord();//结束录音
            //如果录音有效，则进行转换
            if(recordValid){
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        Log.d(TAG,"开始转换");
                        conventMp3(rawPath, mp3Path);//转换mp3
                        if(null != MediaPlugin.this.voiceCallback){
                            recordTrans = false;
                            MediaPlugin.this.voiceCallback.sendPluginResult(result);
                        }

                    }
                });
            }else{
                if(null != MediaPlugin.this.voiceCallback){
                    recordTrans = false;
                    //无效，则直接返回
                    MediaPlugin.this.voiceCallback.sendPluginResult(result);
                }

            }


        }else if(RECORD_STATUS_NORECORD.equals(type)){
            result.setKeepCallback(false);
            this.voiceCallback.sendPluginResult(result);
        }else{
            if(null != this.voiceCallback){
                this.voiceCallback.sendPluginResult(result);
            }

        }


        Log.d(TAG, "updateVoice:" + voice);
//        webView.postMessage(EVENT_TYPE_VOICE,voice);//给其他插件传递消息
    }

    /**
     * 停止录音
     * @param data
     * @param callbackContext
     */
    private void stopRecordAction(JSONArray data, CallbackContext callbackContext) {

        stopRecord();
//        Uri uri = Uri.fromFile(new File(wavPath));
//        Uri uri = Uri.fromFile(new File(mp3Path));
//        Uri uri = Uri.fromFile(new File(rawPath));
//        callbackContext.success(uri.toString());
    }

    /**
     * 结束录音，并转换文件
     */
    private void stopRecord(){
        if(isGetVoiceRun){
            isGetVoiceRun = false;
            //mAudioRecord
            if(null != mAudioRecord){
                mAudioRecord.stop();//停止录音
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }




    }

    /**
     * raw 转mp3
     */
    private boolean conventMp3(String rawPath,String mp3Path){
        /* FLameUtils lameUtils = new FLameUtils(AudioFormat.CHANNEL_IN_DEFAULT, SAMPLE_RATE_IN_HZ, 96);
        boolean result = lameUtils.raw2mp3(rawPath, mp3Path);
        Log.d(TAG, "convent result:" + result + "--mp3:" + mp3Path);
        return result;*/
        return false;

    }



    /**
     * 开始录音
     */
    private void startRecord() {
        Log.d(TAG, "doInBackground");
        isGetVoiceRun = true;//标记任务正在运行
        recordTrans = true;//事物开始
        if(null != mAudioRecord){
            mAudioRecord.release();
        }
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

        //初始化文件路径
        initFilePath(tempRecordFileName);

        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(rawPath))));
            mAudioRecord.startRecording();

            long time1 = System.currentTimeMillis();
            lastSilenceTime = time1;
            startTime = time1;
            recordValid = false;
            while (isGetVoiceRun) {
                long time2 = System.currentTimeMillis();
                //r是实际读取的数据长度，一般而言r会小于buffersize
                int r = mAudioRecord.read(buffer, 0, BUFFER_SIZE);
                if (AudioRecord.ERROR_INVALID_OPERATION != r) {
                    //计算分贝
                    long v = 0;
                    //先写文件
                    for (int i = 0; i < r; i++) {
                        output.writeShort(buffer[i]);
                        v += buffer[i]*buffer[i];
                    }

                    //100毫秒更新一次
                    if (time2-time1 > 100) {

                        double mean = v / (double) r;
                        double volume = 10 * Math.log10(mean);
//                        publishProgress(volume);//更新音量大小
                        double voice = volume/90;
                        updateVoice(voice,RECORD_STATUS_RECORDING);//更新音量
                        Log.d(TAG, "分贝值:" + volume);
                        if(volume > minVolume){
                            lastSilenceTime = time2;
                            recordValid = true;//如果期间有声音，则录音有效
                        }else{
                            Log.d(TAG, "静默时间:" + (time2 - lastSilenceTime));
                        }
                        time1 = time2;
                    }

                }

                //如果静默超时，则退出录音
                if(time2 - lastSilenceTime > exitPeriod){
                    Log.d(TAG, "no voice and stop record...");
                    //结束录音
                    updateVoice(0.0,RECORD_STATUS_END);
                    return;
                }

                //最长录音时间限制
                if(time2 - startTime >= maxPeriod) {
                    Log.d(TAG, "recode max time coming and stop...");
                    //结束录音
                    updateVoice(0.0,RECORD_STATUS_END);
                    return;
                }
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Log.d(TAG,"关闭文件流");
            if (output != null) {
                try {
                    output.flush();
                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }


        }
        //结束录音
        updateVoice(0.0,RECORD_STATUS_END);

    }

    /**
     * 设置路径，第一个为raw文件，第二个为mp3文件
     *
     * @return
     */
    private void initFilePath(String fileName) {
        try {
            File ext = Environment.getExternalStorageDirectory();

            if (rawPath == null) {
                File raw = new File(ext,fileName + ".pcm");
                raw.createNewFile();
                rawPath = raw.getAbsolutePath();
                raw = null;
            }
            if (mp3Path == null) {
                File mp3 = new File(ext,fileName + ".mp3");
                mp3.createNewFile();
                mp3Path = mp3.getAbsolutePath();
                mp3 = null;
            }

           /* if (wavPath == null) {
                File wav = new File(ext,fileName + ".wav");
                wav.createNewFile();
                wavPath = wav.getAbsolutePath();
                wav = null;
            }*/



            Log.d("rawPath", rawPath);
            Log.d("mp3Path", mp3Path);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }







}
