package com.example.testapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Pollinations AI OAuth 测试应用
 * 支持 PKCE OAuth 登录 + Whisper 转录测试
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String CALLBACK_SCHEME = "pollinations-test";

    private AuthFragment authFragment;
    private TestFragment testFragment;
    private ViewPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 创建 Fragment 实例
        authFragment = new AuthFragment();
        testFragment = new TestFragment();

        // 设置 ViewPager
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        pagerAdapter = new ViewPagerAdapter(this);
        pagerAdapter.addFragment(authFragment, "🔐 登录");
        pagerAdapter.addFragment(testFragment, "🎤 Whisper 测试");
        viewPager.setAdapter(pagerAdapter);

        // 绑定 TabLayout
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(pagerAdapter.getTitles().get(position));
        }).attach();

        Log.d(TAG, "App started");
    }

    /**
     * 处理 OAuth 回调 URL
     * 需要在 AndroidManifest 中配置 intent-filter
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleCallback(intent.getData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查是否是回调
        Uri data = getIntent().getData();
        if (data != null) {
            handleCallback(data);
            // 清除回调数据，避免重复处理
            setIntent(new Intent());
        }
    }

    private void handleCallback(Uri data) {
        if (data == null) return;

        String scheme = data.getScheme();
        Log.d(TAG, "Callback URI: " + data.toString() + " (scheme: " + scheme + ")");

        if (CALLBACK_SCHEME.equals(scheme)) {
            authFragment.handleAuthResult(data);
        } else {
            Log.w(TAG, "Unknown callback scheme: " + scheme);
        }
    }
}
