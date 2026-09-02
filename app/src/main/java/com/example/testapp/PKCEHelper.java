package com.example.testapp;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

/**
 * PKCE (Proof Key for Code Exchange) 辅助类
 * 符合 RFC 7636 标准
 */
public class PKCEHelper {

    private static final String CHALLENGE_METHOD = "S256";
    private static final int VERIFIER_LENGTH = 32; // 生成 32 字节的随机字符串

    /**
     * 生成 PKCE code_verifier（随机字符串，43-128字符）
     */
    public static String generateVerifier() {
        Random random = new Random();
        byte[] bytes = new byte[VERIFIER_LENGTH];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 从 code_verifier 生成 code_challenge（Base64URL(SHA256(verifier))）
     */
    public static String generateChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PKCE challenge", e);
        }
    }

    public static String getChallengeMethod() {
        return CHALLENGE_METHOD;
    }
}
