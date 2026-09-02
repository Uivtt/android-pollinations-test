package com.example.testapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

/**
 * OAuth 登录 Fragment - 使用简化 API
 */
public class AuthFragment extends Fragment {

    private static final String TAG = "AuthFragment";
    private static final String PREFS_NAME = "pollinations_prefs";
    private static final String KEY_TOKEN = "api_token";

    // TODO: 替换为你的 pk_ App Key
    private static final String APP_CLIENT_ID = "pk_your_app_key_here";
    private static final String APP_REDIRECT_URI = "pollinations-test://callback";

    private SharedPreferences prefs;
    private OAuthClient oauthClient;
    private PollinationsApi apiClient;

    private MaterialButton btnLogin;
    private MaterialButton btnUseKey;
    private TextView tvApiKey;
    private TextView tvStatus;
    private TextView tvUserInfo;
    private ProgressBar progressBar;

    private String currentVerifier = null;
    private String currentState = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auth, container, false);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        initViews(view);
        setupListeners();
        checkSavedToken();

        return view;
    }

    private void initViews(View view) {
        btnLogin = view.findViewById(R.id.btnLogin);
        btnUseKey = view.findViewById(R.id.btnUseKey);
        tvApiKey = view.findViewById(R.id.etApiKey);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvUserInfo = view.findViewById(R.id.tvUserInfo);
        progressBar = view.findViewById(R.id.progressBar);

        tvApiKey.setHint("在此输入 sk_ 或 pk_ 密钥");
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> startOAuthFlow());

        btnUseKey.setOnClickListener(v -> {
            String key = tvApiKey.getText().toString().trim();
            if (!key.isEmpty()) {
                saveToken(key);
                apiClient = new PollinationsApi(key);
                checkKeyInfo();
            } else {
                Toast.makeText(requireContext(), "请输入 API Key", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startOAuthFlow() {
        if (APP_CLIENT_ID.equals("pk_your_app_key_here")) {
            Toast.makeText(requireContext(),
                    "请先在代码中配置你的 pk_ App Key",
                    Toast.LENGTH_LONG).show();
            return;
        }

        oauthClient = new OAuthClient(requireContext(),
                APP_CLIENT_ID, APP_REDIRECT_URI, "profile usage keys");
        OAuthClient.AuthRequest authRequest = oauthClient.buildAuthRequest();

        currentVerifier = authRequest.verifier;
        currentState = authRequest.state;

        new CustomTabsIntent.Builder()
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(requireContext(), Uri.parse(authRequest.authUrl));

        showProgress(true);
        setStatus("等待授权...");
    }

    public void handleAuthResult(Uri data) {
        if (data == null) return;

        String code = data.getQueryParameter("code");
        String state = data.getQueryParameter("state");
        String error = data.getQueryParameter("error");

        if (error != null) {
            showError("授权失败: " + error);
            return;
        }
        if (code == null) {
            showError("未收到授权码");
            return;
        }
        if (currentState != null && !currentState.equals(state)) {
            showError("State 不匹配");
            return;
        }

        if (oauthClient != null && currentVerifier != null) {
            showProgress(true);
            setStatus("正在交换 Token...");
            oauthClient.exchangeCode(code, currentVerifier, new OAuthClient.AuthCallback() {
                @Override
                public void onSuccess(String jsonResponse) {
                    try {
                        int tokenStart = jsonResponse.indexOf("\"access_token\":\"");
                        if (tokenStart >= 0) {
                            tokenStart += 18;
                            int tokenEnd = jsonResponse.indexOf("\"", tokenStart);
                            if (tokenEnd > tokenStart) {
                                String accessToken = jsonResponse.substring(tokenStart, tokenEnd);
                                saveToken(accessToken);
                                apiClient = new PollinationsApi(accessToken);
                                checkKeyInfo();
                                return;
                            }
                        }
                        showError("解析 Token 失败");
                    } catch (Exception e) {
                        showError("解析 Token 失败: " + e.getMessage());
                    }
                }

                @Override
                public void onError(String message) {
                    showError("Token 交换失败: " + message);
                }
            });
        }
    }

    private void checkKeyInfo() {
        if (apiClient == null) return;
        apiClient.getKeyInfo(new PollinationsApi.KeyInfoCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    int validStart = json.indexOf("\"valid\":");
                    boolean valid = json.contains("\"valid\":true");
                    String type = extractJsonString(json, "\"type\":\"");
                    String name = extractJsonString(json, "\"name\":\"");
                    String expiresAt = extractJsonString(json, "\"expiresAt\":\"");

                    setStatus(valid ? "✅ 已连接" : "❌ Key 无效");
                    tvUserInfo.setText("类型: " + type + (name.isEmpty() ? "" : " | " + name)
                            + " | 过期: " + expiresAt);
                } catch (Exception e) {
                    tvUserInfo.setText(json.length() > 50 ? json.substring(0, 50) : json);
                }
                showProgress(false);
            }

            @Override
            public void onError(String error) {
                showError("获取 Key 信息失败: " + error);
                showProgress(false);
            }
        });
    }

    private void checkSavedToken() {
        String savedToken = prefs.getString(KEY_TOKEN, null);
        if (savedToken != null && !savedToken.isEmpty()) {
            tvApiKey.setText(savedToken);
            apiClient = new PollinationsApi(savedToken);
            checkKeyInfo();
        }
    }

    private void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
        tvApiKey.setText(token);
        Toast.makeText(requireContext(), "Token 已保存", Toast.LENGTH_SHORT).show();
    }

    private String extractJsonString(String json, String key) {
        try {
            int start = json.indexOf(key);
            if (start >= 0) {
                start += key.length();
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private void setStatus(String text) {
        tvStatus.post(() -> tvStatus.setText(text));
    }

    private void showError(String message) {
        tvStatus.post(() -> {
            tvStatus.setTextColor(0xFFD32F2F);
            tvStatus.setText("❌ " + message);
        });
        showProgress(false);
        Log.e(TAG, message);
    }

    private void showProgress(boolean show) {
        progressBar.post(() -> progressBar.setVisibility(show ? View.VISIBLE : View.GONE));
    }
}
