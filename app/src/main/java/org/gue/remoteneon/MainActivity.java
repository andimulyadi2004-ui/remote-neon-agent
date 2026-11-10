package org.gue.remoteneon;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 1;
    private static final int REQ_SCREEN = 2;

    private EditText etServerIP;
    private TextView tvStatus;
    private Button btnAccessibility, btnAudio, btnScreen, btnStart;

    private int resultCode = Activity.RESULT_CANCELED;
    private Intent data = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerIP = findViewById(R.id.etServerIP);
        tvStatus = findViewById(R.id.tvStatus);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnAudio = findViewById(R.id.btnAudio);
        btnScreen = findViewById(R.id.btnScreen);
        btnStart = findViewById(R.id.btnStart);

        SharedPreferences prefs = getSharedPreferences("remote_neon", MODE_PRIVATE);
        String savedIP = prefs.getString("server_ip", "192.168.1.10");
        etServerIP.setText(savedIP);

        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Aktifkan 'Remote Neon' di Accessibility", Toast.LENGTH_LONG).show();
        });

        btnAudio.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            } else {
                tvStatus.setText("Audio sudah diizinkan");
            }
        });

        btnScreen.setOnClickListener(v -> {
            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_SCREEN);
        });

        btnStart.setOnClickListener(v -> {
            String ip = etServerIP.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Masukkan IP server dulu", Toast.LENGTH_SHORT).show();
                return;
            }

            if (resultCode != Activity.RESULT_OK || data == null) {
                Toast.makeText(this, "Izinkan rekam layar dulu", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit().putString("server_ip", ip).apply();

            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("server_ip", ip);
            serviceIntent.putExtra("result_code", resultCode);
            serviceIntent.putExtra("data", data);
            startForegroundService(serviceIntent);

            Toast.makeText(this, "Service dimulai", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_AUDIO) {
            if (resultCode == Activity.RESULT_OK) {
                tvStatus.setText("Audio diizinkan");
            }
        }

        if (requestCode == REQ_SCREEN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                this.resultCode = resultCode;
                this.data = data;
                tvStatus.setText("Layar diizinkan");
            } else {
                tvStatus.setText("Izin layar ditolak");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("Audio diizinkan");
            }
        }
    }
}
