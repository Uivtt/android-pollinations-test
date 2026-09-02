package com.example.testapp;

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

import java.io.ByteArrayOutputStream;

/**
 * Whisper 转录测试 Fragment - 使用简化 API
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

    private void testWithGeneratedAudio() {
        if (!checkApiReady()) return;

        showProgress(true);
        setResult("⏳ 正在转录...（语言: " + selectedLanguage + "）");
        Log.d(TAG, "Transcribing with language: " + selectedLanguage);

        generateAndTranscribe();
    }

    private void generateAndTranscribe() {
        try {
            byte[] wavData = generateSineWaveWav(440, 2.0f, 16000);

            if (apiClient != null) {
                apiClient.transcribeAudio(wavData, selectedLanguage,
                        new PollinationsApi.AudioCallback() {
                            @Override
                            public void onSuccess(String text) {
                                showProgress(false);
                                setResult(text);
                                tvDetails.setText("成功转录 | 语言: " + selectedLanguage
                                        + " | 模型: whisper");
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

    private byte[] generateSineWaveWav(int frequency, float duration, int sampleRate) throws Exception {
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
        baos.writeInt(Integer.reverseBytes(36 + dataSize));
        writeString(baos, "WAVE");

        // fmt sub-chunk
        writeString(baos, "fmt ");
        baos.writeInt(Integer.reverseBytes(16));
        baos.writeShort(Short.reverseBytes((short) 1));
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
            int sampleValue = (int) (sample * 32767);
            baos.writeShort(Short.reverseBytes((short) sampleValue));
        }

        return baos.toByteArray();
    }

    private void writeString(ByteArrayOutputStream bout, String s) throws Exception {
        for (int i = 0; i < s.length(); i++) {
            bout.write(s.charAt(i));
        }
    }

    private boolean checkApiReady() {
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
        btnTranscribeTone.post(() -> btnTranscribeTone.setEnabled(!show));
    }
}
