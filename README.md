# Pollinations AI OAuth Android Demo

Pollinations AI 移动端 OAuth + Whisper 转录测试应用。

## 功能
- 🔐 PKCE OAuth 2.1 登录流程（支持 Chrome Custom Tabs）
- 🎤 Whisper Large V3 音频转录测试
- 💾 Token 安全存储（SharedPreferences）
- 🌐 多语言支持（中/英/日/韩）

## 项目结构
```
android-test/
├── .github/workflows/build.yml   # GitHub Actions 远程构建
├── app/
│   ├── build.gradle              # 依赖配置
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限 + 自定义 Scheme
│       └── java/com/example/testapp/
│           ├── MainActivity.java        # 主界面（双 Tab）
│           ├── AuthFragment.java        # OAuth 登录页
│           ├── TestFragment.java        # Whisper 测试页
│           ├── OAuthClient.java         # OAuth 客户端
│           ├── PollinationsApi.java     # API 调用封装
│           └── PKCEHelper.java          # PKCE 工具类
└── README.md
```

## 构建方式

### 方式一：本地构建（需要 Android Studio + JDK 17）
```bash
cd android-test
./gradlew assembleRelease
# APK 位于 app/build/outputs/apk/release/app-release-unsigned.apk
```

### 方式二：GitHub Actions 远程构建
1. 将此项目推送至 GitHub 仓库
2. 在 Actions 标签页手动触发 `Build APK` workflow
3. 从 Artifact 下载 APK

## 配置步骤

### 1. 创建 App Key
访问 https://enter.pollinations.ai/keys 创建：
- **Redirect URI**: `pollinations-test://callback`
- **Scopes**: `profile usage keys`

### 2. 修改 App 中的 client_id
编辑 `AuthFragment.java`，将：
```java
String clientId = "pk_test_placeholder";
```
替换为你创建的真实 `pk_...` key。

### 3. 测试用 sk_ Key
当前已预填测试密钥 ``sk_你的密钥``，可直接跳过 OAuth 测试 Whisper 转录。

## OAuth Flow 说明
```
App → 生成 PKCE verifier/challenge
    → 打开 enter.pollinations.ai/authorize（Chrome Custom Tabs）
    → 用户登录授权
    → 重定向到 pollinations-test://callback?code=xxx
    → App 用 code + verifier 换取 sk_ token
    → Token 存入 SharedPreferences，后续 API 调用使用
```

## 依赖
- OkHttp 4.12.0
- Material Components 1.11.0
- AndroidX Browser 1.8.0
- Gson 2.10.1
