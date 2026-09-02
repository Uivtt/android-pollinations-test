package com.example.testapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = findViewById(R.id.main_text);
        if (tv != null) {
            tv.setText("Hello from " + BuildConfig.APPLICATION_ID + " v" + BuildConfig.VERSION_NAME);
        }
    }
}
