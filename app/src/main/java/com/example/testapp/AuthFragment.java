package com.example.testapp;

import android.app.Activity;
import android.content.Intent;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * OAuth 登录 Fragment
 * 支持 PKCE 流程 + 手动 Key 输入两种方式
 * 
 * ⚠️ 安全提醒：sk_ 密钥永远不要硬编码在源码中！
 *    正式 App 应从服务端或安全存储获取
 */
public class AuthFragment extends Fragment {

    private static final String TAG = "AuthFragment";
    private static final String PREFS_NAME = "pollinations_prefs";
    private static final String KEY_TOKEN = "api_token";

    // TODO: 替换为你的 pk_ App Key（在 https://enter.pollinations.ai/keys 创建）
    private static final String APP_CLIENT_ID = "pk_your_app_key_here";
    // TODO: 替换为你的回调 URL
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

        // 提示用户填入自己的 Key
        tvApiKey.setHint("在此输入 sk_ 或 pk_ 密钥");
    }

    private void setupListeners() {
        // PKCE 登录按钮
        btnLogin.setOnClickListener(v -> startOAuthFlow());

        // 手动使用 Key 按钮
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

    /**
     * 启动 PKCE OAuth 流程
     */
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

    /**
     * 处理 OAuth 回调
     */
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
            showError("State 不匹配，可能存在 CSRF 攻击");
            return;
        }

        if (oauthClient != null && currentVerifier != null) {
            showProgress(true);
            setStatus("正在交换 Token...");
            oauthClient.exchangeCode(code, currentVerifier, new OAuthClient.AuthCallback() {
                @Override
                public void onSuccess(JSONObject tokenResponse) {
                    try {
                        String accessToken = tokenResponse.getString("access_token");
                        long expiresIn = tokenResponse.getLong("expires_in");
                        saveToken(accessToken);
                        apiClient = new PollinationsApi(accessToken);
                        checkKeyInfo();
                    } catch (JSONException e) {
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
            public void onSuccess(JSONObject info) {
                try {
                    boolean valid = info.getBoolean("valid");
                    String type = info.optString("type", "");
                    String name = info.optString("name", "");
                    String expiresAt = info.optString("expiresAt", "永不过期");

                    setStatus(valid ? "✅ 已连接" : "❌ Key 无效");
                    tvUserInfo.setText("类型: " + type
                            + (name.isEmpty() ? "" : " | " + name)
                            + " | 过期: " + expiresAt);
                } catch (JSONException e) {
                    tvUserInfo.setText(info.optString("message", "Unknown"));
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
