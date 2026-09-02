package com.example.testapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pollinations AI API 客户端 - 使用 Java 标准库
 */
public class PollinationsApi {

    private static final String TAG = "PollinationsApi";
    private static final String BASE_URL = "https://gen.pollinations.ai/v1";

    private final String apiKey;
    private final ExecutorService executor;

    public PollinationsApi(String apiKey) {
        this.apiKey = apiKey;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Whisper 音频转录
     */
    public void transcribeAudio(byte[] audioData, String language, AudioCallback callback) {
        executor.execute(() -> {
            try {
                String boundary = "----FormBoundary" + System.currentTimeMillis();
                String header = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"model\"\r\n\r\nwhisper\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                        + "Content-Type: audio/wav\r\n\r\n";
                String footer = "\r\n--" + boundary + "--\r\n";
                
                StringBuilder bodyBuilder = new StringBuilder(header);
                if (language != null && !language.isEmpty()) {
                    bodyBuilder.append("--").append(boundary).append("\r\n")
                            .append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
                            .append(language).append("\r\n");
                }
                bodyBuilder.append(footer);

                byte[] headerBytes = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);
                byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(headerBytes);
                baos.write(audioData);
                baos.write(footerBytes);
                byte[] body = baos.toByteArray();

                URL url = new URL(BASE_URL + "/audio/transcriptions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setFixedLengthStreamingMode(body.length);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int responseCode = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String response = sb.toString();
                Log.d(TAG, "Transcription response: " + responseCode + " - " + response);
                
                if (responseCode == 200) {
                    // Extract text from JSON response
                    String text = extractTextFromJson(response);
                    callback.onSuccess(text);
                } else {
                    callback.onError("HTTP " + responseCode + ": " + response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Transcription failed", e);
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
                URL url = new URL(BASE_URL + "/account/key");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);

                int responseCode = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                String response = sb.toString();
                Log.d(TAG, "Key info response: " + responseCode + " - " + response);
                
                if (responseCode == 200) {
                    callback.onSuccess(response);
                } else {
                    callback.onError("HTTP " + responseCode + ": " + response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Get key info failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private String extractTextFromJson(String json) {
        try {
            int textStart = json.indexOf("\"text\":\"");
            if (textStart >= 0) {
                textStart += 8;
                int textEnd = json.indexOf("\"", textStart);
                if (textEnd > textStart) {
                    return json.substring(textStart, textEnd);
                }
            }
            return json;
        } catch (Exception e) {
            return json;
        }
    }

    public interface AudioCallback {
        void onSuccess(String text);
        void onError(String error);
    }

    public interface KeyInfoCallback {
        void onSuccess(String json);
        void onError(String error);
    }
}
