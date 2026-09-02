package com.example.testapp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Pollinations AI OAuth 客户端
 * 实现 RFC 6749 Authorization Code Flow with PKCE
 */
public class OAuthClient {

    private static final String TAG = "OAuthClient";

    // Pollinations OAuth 端点（从 discovery 获取）
    private static final String AUTHORIZATION_ENDPOINT =
            "https://enter.pollinations.ai/authorize";
    private static final String TOKEN_ENDPOINT =
            "https://enter.pollinations.ai/api/oauth/token";
    private static final String USERINFO_ENDPOINT =
            "https://enter.pollinations.ai/api/oauth/userinfo";

    private final String clientId;
    private final String redirectUri;
    private final String scopes;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;

    public OAuthClient(Context context, String clientId, String redirectUri, String scopes) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 构建授权 URL，用于 Chrome Custom Tabs 打开
     */
    public AuthRequest buildAuthRequest() {
        String verifier = PKCEHelper.generateVerifier();
        String challenge = PKCEHelper.generateChallenge(verifier);
        String state = generateRandomString(32);

        Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority("enter.pollinations.ai")
                .appendPath("authorize")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", scopes)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", PKCEHelper.getChallengeMethod());

        return new AuthRequest(builder.build().toString(), verifier, state);
    }

    /**
     * 用授权码换取 access token（sk_ key）
     */
    public void exchangeCode(String code, String verifier, AuthCallback callback) {
        executor.execute(() -> {
            try {
                String body = "grant_type=authorization_code"
                        + "&code=" + URLEncoder.encode(code, "UTF-8")
                        + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                        + "&code_verifier=" + URLEncoder.encode(verifier, "UTF-8");

                Request request = new Request.Builder()
                        .url(TOKEN_ENDPOINT)
                        .post(RequestBody.create(body.getBytes(StandardCharsets.UTF_8),
                                MediaType.get("application/x-www-form-urlencoded")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null) {
                        String json = response.body().string();
                        JSONObject result = new JSONObject(json);
                        if (result.has("access_token")) {
                            callback.onSuccess(result);
                        } else {
                            callback.onError(result.optString("error", "Unknown error")
                                    + ": " + result.optString("error_description", ""));
                        }
                    } else {
                        callback.onError("Empty response: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Token exchange failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    /**
     * 获取当前用户信息（需要有效 token）
     */
    public void getUserInfo(String accessToken, UserInfoCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(USERINFO_ENDPOINT)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null) {
                        callback.onSuccess(response.body().string());
                    } else {
                        callback.onError("Empty userinfo response: " + response.code());
                    }
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public interface AuthCallback {
        void onSuccess(JSONObject tokenResponse);
        void onError(String message);
    }

    public interface UserInfoCallback {
        void onSuccess(String userInfo);
        void onError(String message);
    }

    public static class AuthRequest {
        public final String authUrl;
        public final String verifier;
        public final String state;

        public AuthRequest(String authUrl, String verifier, String state) {
            this.authUrl = authUrl;
            this.verifier = verifier;
            this.state = state;
        }
    }

    private static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return URLEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                StandardCharsets.UTF_8.name());
    }
}
