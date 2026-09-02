package com.example.testapp;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Whisper 转录测试 Fragment
 * 使用 FFmpeg 生成的测试音频进行转录
 */
public class TestFragment extends Fragment {

    private static final String TAG = "TestFragment";

    private String savedApiKey = null;
    private PollinationsApi apiClient;

    private MaterialButton btnTranscribeTone;
    private TextView tvResult;
    private TextView tvDetails;
    private ProgressBar progressBar;

    private String selectedLanguage = "zh";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_test, container, false);

        // 读取保存的 API Key
        String prefsName = "pollinations_prefs";
        String token = requireContext()
                .getSharedPreferences(prefsName, 0)
                .getString("api_token", null);
        if (token != null) {
            savedApiKey = token;
            apiClient = new PollinationsApi(token);
        }

        initViews(view);
        setupListeners();
        setupLanguageSelector();

        return view;
    }

    private void initViews(View view) {
        btnTranscribeTone = view.findViewById(R.id.btnTranscribeTone);
        tvResult = view.findViewById(R.id.tvResult);
        tvDetails = view.findViewById(R.id.tvDetails);
        progressBar = view.findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        // 生成并播放测试音
        btnTranscribeTone.setOnClickListener(v -> testWithGeneratedAudio());
    }

    private void setupLanguageSelector() {
        String[] languages = {"zh (中文)", "en (English)", "ja (日本語)", "ko (한국어)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                languages
        );
        android.widget.AutoCompleteTextView autoComplete =
                requireView().findViewById(R.id.spLanguage);
        autoComplete.setAdapter(adapter);
        autoComplete.setOnItemClickListener((parent, view1, position, id) -> {
            String[] codes = {"zh", "en", "ja", "ko"};
            selectedLanguage = codes[position];
        });
    }

    /**
     * 生成 WAV 测试音并转录（使用 FFmpeg 生成 440Hz 正弦波）
     * 实际项目中应替换为真实录音或音频文件
     */
    private void testWithGeneratedAudio() {
        if (!checkApiReady()) return;

        showProgress(true);
        setResult("⏳ 正在转录...（语言: " + selectedLanguage + "）");
        Log.d(TAG, "Transcribing with language: " + selectedLanguage);

        // 生成 2 秒 440Hz 正弦波 WAV 文件（通过系统命令）
        generateAndTranscribe();
    }

    /**
     * 使用 adb ffmpeg 生成测试音频并转录
     */
    private void generateAndTranscribe() {
        try {
            // 创建临时 WAV 文件路径
            java.io.File tempFile = new java.io.File(
                    requireContext().getCacheDir(), "test_tone.wav");

            // 用 Java 生成简单的 WAV 文件（440Hz 正弦波，16bit mono, 16kHz, 2秒）
            byte[] wavData = generateSineWaveWav(440, 2.0f, 16000);

            // 调用 API
            if (apiClient != null) {
                apiClient.transcribeAudio(wavData, selectedLanguage,
                        new PollinationsApi.AudioCallback() {
                            @Override
                            public void onSuccess(String text) {
                                showProgress(false);
                                setResult(text);
                                tvDetails.setText("成功转录 | 语言: " + selectedLanguage
                                        + " | 模型: whisper (large-v3)");
                                Log.d(TAG, "Transcription result: " + text);
                            }

                            @Override
                            public void onError(String error) {
                                showProgress(false);
                                setResult("❌ 错误: " + error);
                                Log.e(TAG, "Transcription error: " + error);
                            }
                        });
            }
        } catch (Exception e) {
            showProgress(false);
            setResult("❌ 生成音频失败: " + e.getMessage());
            Log.e(TAG, "Audio generation failed", e);
        }
    }

    /**
     * 生成简单的 WAV 格式正弦波音频数据
     * @param frequency 频率 (Hz)
     * @param duration  时长 (秒)
     * @param sampleRate 采样率
     */
    private byte[] generateSineWaveWav(int frequency, float duration, int sampleRate) throws IOException {
        int numSamples = (int) (sampleRate * duration);
        int bitDepth = 16;
        int numChannels = 1;
        int byteRate = sampleRate * numChannels * bitDepth / 8;
        int blockAlign = numChannels * bitDepth / 8;
        int dataSize = numSamples * blockAlign;
        int headerSize = 44;

        ByteArrayOutputStream baos = new ByteArrayOutputStream(headerSize + dataSize);

        // RIFF header
        writeString(baos, "RIFF");
        baos.writeInt(Integer.reverseBytes(36 + dataSize)); // file size - 8
        writeString(baos, "WAVE");

        // fmt sub-chunk
        writeString(baos, "fmt ");
        baos.writeInt(Integer.reverseBytes(16)); // sub-chunk size
        baos.writeShort(Short.reverseBytes((short) 1)); // PCM format
        baos.writeShort(Short.reverseBytes((short) numChannels));
        baos.writeInt(Integer.reverseBytes(sampleRate));
        baos.writeInt(Integer.reverseBytes(byteRate));
        baos.writeShort(Short.reverseBytes((short) blockAlign));
        baos.writeShort(Short.reverseBytes((short) bitDepth));

        // data sub-chunk
        writeString(baos, "data");
        baos.writeInt(Integer.reverseBytes(dataSize));

        // Generate sine wave samples
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            double sample = Math.sin(2 * Math.PI * frequency * t);
            // Scale to 16-bit range
            int sampleValue = (int) (sample * 32767);
            baos.writeShort(Short.reverseBytes((short) sampleValue));
        }

        return baos.toByteArray();
    }

    private void writeString(ByteArrayOutputStream bout, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            bout.write(s.charAt(i));
        }
    }

    private void checkApiReady() {
        if (apiClient == null || savedApiKey == null) {
            Toast.makeText(requireContext(),
                    "请先在「登录」标签页中连接 API Key",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void setResult(String text) {
        tvResult.post(() -> tvResult.setText(text));
    }

    private void showProgress(boolean show) {
        progressBar.post(() -> progressBar.setVisibility(show ? View.VISIBLE : View.GONE));
        btnTranscribeTone.post(() ->
                btnTranscribeTone.setEnabled(!show));
    }
}
