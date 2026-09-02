package com.example.testapp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pollinations AI API 客户端
 * OpenAI 兼容接口
 */
public class PollinationsApi {

    private static final String TAG = "PollinationsApi";
    private static final String BASE_URL = "https://gen.pollinations.ai/v1";

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;

    public PollinationsApi(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Whisper 音频转录
     * @param audioData 音频文件的字节数组（支持 wav/mp3/m4a 等）
     * @param language  可选，如 "zh" 或 "en"
     * @param callback  结果回调
     */
    public void transcribeAudio(byte[] audioData, String language, AudioCallback callback) {
        executor.execute(() -> {
            try {
                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", "whisper")
                        .addFormDataPart("file", "audio.wav",
                                RequestBody.create(audioData, MediaType.parse("audio/wav")));

                if (language != null && !language.isEmpty()) {
                    builder.addFormDataPart("language", language);
                }

                Request request = new Request.Builder()
                        .url(BASE_URL + "/audio/transcriptions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(builder.build())
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null) {
                        String text = response.body().string();
                        Log.d(TAG, "Transcription response: " + text);
                        try {
                            JSONObject json = new JSONObject(text);
                            callback.onSuccess(json.optString("text", ""));
                        } catch (JSONException e) {
                            callback.onSuccess(text);
                        }
                    } else {
                        callback.onError("Empty response: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    /**
     * Whisper 转录（带详细返回，含时间戳）
     */
    public void transcribeVerbose(byte[] audioData, String language, VerboseCallback callback) {
        executor.execute(() -> {
            try {
                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", "whisper")
                        .addFormDataPart("file", "audio.wav",
                                RequestBody.create(audioData, MediaType.parse("audio/wav")))
                        .addFormDataPart("response_format", "verbose_json");

                if (language != null && !language.isEmpty()) {
                    builder.addFormDataPart("language", language);
                }

                Request request = new Request.Builder()
                        .url(BASE_URL + "/audio/transcriptions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(builder.build())
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null) {
                        String json = response.body().string();
                        JSONObject result = new JSONObject(json);
                        callback.onSuccess(result);
                    } else {
                        callback.onError("Empty response: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Verbose transcription failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    /**
     * 获取当前 key 信息
     */
    public void getKeyInfo(KeyInfoCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/account/key")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null) {
                        String json = response.body().string();
                        callback.onSuccess(new JSONObject(json));
                    } else {
                        callback.onError("Empty response: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Get key info failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    public interface AudioCallback {
        void onSuccess(String text);
        void onError(String error);
    }

    public interface VerboseCallback {
        void onSuccess(JSONObject result);
        void onError(String error);
    }

    public interface KeyInfoCallback {
        void onSuccess(JSONObject info);
        void onError(String error);
    }
}
