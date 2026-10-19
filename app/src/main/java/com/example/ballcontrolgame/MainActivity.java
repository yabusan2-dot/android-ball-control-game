package com.example.ballcontrolgame;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import com.example.ballcontrolgame.databinding.ActivityMainBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "BallControlGame";
    private static final String DB_URL = "https://shootinggame-sample-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String LOCAL_SERVER_URL = "http://10.17.142.117:8081"; // PCのIPアドレス
    private static final String PROXY_HOST = "10.1.1.5";
    private static final int PROXY_PORT = 8080;

    private ActivityMainBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private GameView gameView;
    private DatabaseReference databaseReference;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean isFirebaseOnline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. プロキシの徹底設定
        setupGlobalProxy();

        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupWebViewProxyAndFirebase();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        binding.startButton.setOnClickListener(v -> startGame());
    }

    private void setupGlobalProxy() {
        System.setProperty("http.proxyHost", PROXY_HOST);
        System.setProperty("http.proxyPort", String.valueOf(PROXY_PORT));
        System.setProperty("https.proxyHost", PROXY_HOST);
        System.setProperty("https.proxyPort", String.valueOf(PROXY_PORT));
        
        System.setProperty("sun.net.spi.nameservice.nameservers", PROXY_HOST);

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication("", "".toCharArray());
                }
                return super.getPasswordAuthentication();
            }
        });

        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String host = uri.getHost();
                if (host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("10.17.142.117"))) {
                    return Collections.singletonList(Proxy.NO_PROXY);
                }
                return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                Log.e(TAG, "ProxySelector: Connection failed for " + uri, ioe);
            }
        });
    }

    private void setupWebViewProxyAndFirebase() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(PROXY_HOST + ":" + PROXY_PORT)
                    .addDirect()
                    .build();

            ProxyController.getInstance().setProxyOverride(proxyConfig, Runnable::run, () -> {
                Log.d(TAG, "WebView Proxy override successful");
                initFirebase();
            });
        } else {
            initFirebase();
        }
    }

    private void initFirebase() {
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance(DB_URL);
            database.setPersistenceEnabled(false);
            databaseReference = database.getReference("ranking_ball");
            
            database.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    isFirebaseOnline = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                    Log.d(TAG, "Firebase Connection: " + (isFirebaseOnline ? "ONLINE" : "OFFLINE"));
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Firebase Connection Error: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization failed", e);
        }
    }

    private void startGame() {
        binding.startButton.setVisibility(View.GONE);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);

        gameView = new GameView(this);
        gameView.setGameListener(this::onGoalReached);
        binding.gameContainer.addView(gameView);

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void onGoalReached(String time) {
        if (sensorManager != null) sensorManager.unregisterListener(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GOAL!!");
        builder.setMessage("タイム: " + time + "\nお名前を入力してください");
        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("保存してランキングを表示", (dialog, which) -> {
            String name = input.getText().toString().trim();
            saveScore(name.isEmpty() ? "名無し" : name, time);
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void saveScore(String name, String time) {
        if (databaseReference != null && isFirebaseOnline) {
            saveScoreToFirebase(name, time);
        } else {
            saveScoreToLocalServer(name, time);
        }
    }

    private void saveScoreToFirebase(String name, String time) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Firebaseに保存中...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String id = databaseReference.push().getKey();
        RankingEntry entry = new RankingEntry(name, time, System.currentTimeMillis());

        Runnable timeoutRunnable = () -> {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
                Log.w(TAG, "Firebase Save timeout. Trying local server.");
                saveScoreToLocalServer(name, time);
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);

        if (id != null) {
            databaseReference.child(id).setValue(entry)
                .addOnCompleteListener(task -> {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                        if (task.isSuccessful()) {
                            showRankingDialog(time);
                        } else {
                            saveScoreToLocalServer(name, time);
                        }
                    }
                });
        }
    }

    private void saveScoreToLocalServer(String name, String time) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("ローカルサーバーに保存中...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            boolean success = false;
            String serverResponse = "";
            try {
                URL url = new URL(LOCAL_SERVER_URL + "/api/add_score");
                // ローカル通信なのでプロキシを絶対に通さない
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("timeString", time);
                json.put("timestamp", System.currentTimeMillis());

                Log.d(TAG, "Local server request body: " + json.toString());

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Local server response code: " + responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    success = true;
                    try (Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                        serverResponse = s.hasNext() ? s.next() : "";
                    }
                    Log.d(TAG, "Local server response: " + serverResponse);
                } else {
                    try (Scanner s = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A")) {
                        serverResponse = s.hasNext() ? s.next() : "";
                    }
                    Log.e(TAG, "Local server error response: " + serverResponse);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Local server save FAILED", e);
                serverResponse = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalMsg = serverResponse;
            runOnUiThread(() -> {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                if (!finalSuccess) {
                    Toast.makeText(this, "保存失敗: " + finalMsg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "サーバーに保存しました", Toast.LENGTH_SHORT).show();
                }
                showRankingDialog(time);
            });
        }).start();
    }

    private void showRankingDialog(String yourTime) {
        if (isFirebaseOnline && databaseReference != null) {
            showRankingFromFirebase(yourTime);
        } else {
            showRankingFromLocalServer(yourTime);
        }
    }

    private void showRankingFromFirebase(String yourTime) {
        ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("ランキング取得中...");
        loadingDialog.show();

        Runnable loadingTimeout = () -> {
            if (loadingDialog.isShowing()) {
                loadingDialog.dismiss();
                showRankingFromLocalServer(yourTime);
            }
        };
        timeoutHandler.postDelayed(loadingTimeout, 10000);

        Query topScores = databaseReference.orderByChild("time").limitToFirst(10);
        topScores.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                timeoutHandler.removeCallbacks(loadingTimeout);
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                    StringBuilder sb = new StringBuilder();
                    sb.append("あなたのタイム: ").append(yourTime).append("\n\n");
                    sb.append("--- Firebase TOP 10 ---\n");
                    int rankCount = 1;
                    for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                        RankingEntry entry = postSnapshot.getValue(RankingEntry.class);
                        if (entry != null) {
                            sb.append(rankCount).append(". ").append(entry.name).append(" - ").append(entry.getTimeString()).append("\n");
                            rankCount++;
                        }
                    }
                    displayRankingDialog(sb.toString());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                timeoutHandler.removeCallbacks(loadingTimeout);
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                    showRankingFromLocalServer(yourTime);
                }
            }
        });
    }

    private void showRankingFromLocalServer(String yourTime) {
        ProgressDialog loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("ローカルランキング取得中...");
        loadingDialog.show();

        new Thread(() -> {
            String resultText = "ローカルサーバーからデータを取得できませんでした。";
            try {
                URL url = new URL(LOCAL_SERVER_URL + "/api/top10");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "[]";
                    JSONArray jsonArray = new JSONArray(response);
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("あなたのタイム: ").append(yourTime).append("\n\n");
                    sb.append("--- Local Server TOP 10 ---\n");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        sb.append(i + 1).append(". ").append(obj.getString("name"))
                          .append(" - ").append(obj.getString("timeString")).append("\n");
                    }
                    resultText = sb.toString();
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Local server rank load FAILED", e);
            }

            final String finalResult = resultText;
            runOnUiThread(() -> {
                loadingDialog.dismiss();
                displayRankingDialog(finalResult);
            });
        }).start();
    }

    private void displayRankingDialog(String message) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("ランキング")
                .setMessage(message)
                .setPositiveButton("リトライ", (dialog, which) -> startGame())
                .setNegativeButton("終了", (dialog, which) -> finish())
                .setCancelable(false).show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (gameView != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gameView.updateSensorValues(event.values[0], event.values[1]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }
}
