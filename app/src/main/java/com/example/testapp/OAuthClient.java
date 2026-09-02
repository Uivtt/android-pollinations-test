package com.example.testapp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pollinations AI OAuth 客户端 - 使用 Java 标准库
 */
public class OAuthClient {

    private static final String TAG = "OAuthClient";
    private static final String AUTHORIZATION_ENDPOINT =
            "https://enter.pollinations.ai/authorize";
    private static final String TOKEN_ENDPOINT =
            "https://enter.pollinations.ai/api/oauth/token";

    private final String clientId;
    private final String redirectUri;
    private final String scopes;
    private final ExecutorService executor;

    public OAuthClient(Context context, String clientId, String redirectUri, String scopes) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.executor = Executors.newSingleThreadExecutor();
    }

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

    public void exchangeCode(String code, String verifier, AuthCallback callback) {
        executor.execute(() -> {
            try {
                String body = "grant_type=authorization_code"
                        + "&code=" + URLEncoder.encode(code, "UTF-8")
                        + "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                        + "&code_verifier=" + URLEncoder.encode(verifier, "UTF-8");

                URL url = new URL(TOKEN_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
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
                Log.d(TAG, "Token response: " + responseCode + " - " + response);
                
                if (responseCode == 200) {
                    callback.onSuccess(response);
                } else {
                    callback.onError("HTTP " + responseCode + ": " + response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Token exchange failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    public interface AuthCallback {
        void onSuccess(String jsonResponse);
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
