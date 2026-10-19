package com.example.ballcontrolgame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameView extends View {
    private final Paint ballPaint;
    private final Paint wallPaint;
    private final Paint goalPaint;
    private final Paint textPaint;
    private final Paint trapPaint;
    private final Paint timerPaint;

    private float ballX, ballY;
    private final float ballRadius = 40f;
    private float ballVX, ballVY;

    private final List<RectF> walls;
    private final List<RectF> traps;
    private RectF goal;

    private boolean isGameOver = false;
    private long startTime;
    private long endTime;

    public interface GameListener {
        void onGoal(String time);
    }
    private GameListener gameListener;

    public void setGameListener(GameListener listener) {
        this.gameListener = listener;
    }

    public GameView(Context context) {
        super(context);
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint = new Paint();
        wallPaint.setColor(Color.DKGRAY);
        goalPaint = new Paint();
        goalPaint.setColor(Color.YELLOW);
        textPaint = new Paint();
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(100f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        trapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trapPaint.setColor(Color.BLACK);

        timerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        timerPaint.setColor(Color.BLACK);
        timerPaint.setTextSize(50f);

        walls = new ArrayList<>();
        traps = new ArrayList<>();
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        resetBall();
        
        walls.clear();
        traps.clear();
        
        float sw = (float) w;
        float sh = (float) h;
        float gap = ballRadius * 3.5f;
        float wallT = 20f;
        
        // 外壁
        walls.add(new RectF(0, 0, sw, wallT));
        walls.add(new RectF(0, 0, wallT, sh));
        walls.add(new RectF(sw - wallT, 0, sw, sh));
        walls.add(new RectF(0, sh - wallT, sw, sh));
        
        float h1 = sh / 3f;
        float h2 = 2f * sh / 3f;
        float w1 = sw / 3f;
        float w2 = 2f * sw / 3f;
        
        // 水平仕切り
        walls.add(new RectF(wallT, h1 - wallT/2, sw - gap, h1 + wallT/2));
        walls.add(new RectF(gap, h2 - wallT/2, sw - wallT, h2 + wallT/2));
        
        // 垂直仕切り
        walls.add(new RectF(w1 - wallT/2, wallT, w1 + wallT/2, h1 - gap));
        walls.add(new RectF(w2 - wallT/2, gap, w2 + wallT/2, h1 - wallT));
        walls.add(new RectF(w2 - wallT/2, h1 + gap, w2 + wallT/2, h2 - wallT));
        walls.add(new RectF(w1 - wallT/2, h1 + wallT, w1 + wallT/2, h2 - gap));
        walls.add(new RectF(w1 - wallT/2, h2 + wallT, w1 + wallT/2, sh - gap));
        walls.add(new RectF(w2 - wallT/2, h2 + gap, w2 + wallT/2, sh - wallT));
        
        // トラップ配置 (1段目: 1個)
        addTrapsInSection(w1, 0, w2, h1, 1);
        addTrapsInSection(w2, 0, sw, h1, 1);
        // (2段目: 左右の壁沿いで高さランダム、かつ入出口を避ける)
        addTrapsToVerticalWallsRandomY(w2, h1, sw, h2, true, false, true, false);
        addTrapsToVerticalWallsRandomY(w1, h1, w2, h2, false, true, true, false);
        addTrapsToVerticalWallsRandomY(wallT, h1, w1, h2, false, true, false, true);
        // (3段目: 左右壁沿い2個 + 中央付近1個、かつ入出口を避ける)
        addTrapsLowerSection(0, h2, w1, sh, true, false, false, true);
        addTrapsLowerSection(w1, h2, w2, sh, false, true, true, false);
        
        goal = new RectF(sw - 140, sh - 140, sw - 40, sh - 40);
    }

    private void addTrapsToVerticalWallsRandomY(float l, float t, float r, float b, 
                                               boolean topGapL, boolean bottomGapL,
                                               boolean topGapR, boolean bottomGapR) {
        float trapRadius = ballRadius * 1.2f;
        float padding = 5f;
        float gap = ballRadius * 3.5f;
        float avoidDist = gap + ballRadius * 4f; // 入出口からボール2つ分以上の距離を確保

        // 左壁沿い
        float minYL = topGapL ? t + avoidDist : t + trapRadius * 2;
        float maxYL = bottomGapL ? b - avoidDist : b - trapRadius * 2;
        if (maxYL > minYL) {
            float ly = minYL + (float)Math.random() * (maxYL - minYL);
            traps.add(new RectF(l + padding, ly - trapRadius, l + padding + trapRadius * 2, ly + trapRadius));
        }
        
        // 右壁沿い
        float minYR = topGapR ? t + avoidDist : t + trapRadius * 2;
        float maxYR = bottomGapR ? b - avoidDist : b - trapRadius * 2;
        if (maxYR > minYR) {
            float ry = minYR + (float)Math.random() * (maxYR - minYR);
            traps.add(new RectF(r - padding - trapRadius * 2, ry - trapRadius, r - padding, ry + trapRadius));
        }
    }

    private void addTrapsLowerSection(float l, float t, float r, float b,
                                     boolean topGapL, boolean bottomGapL,
                                     boolean topGapR, boolean bottomGapR) {
        // 左右壁沿い
        addTrapsToVerticalWallsRandomY(l, t, r, b, topGapL, bottomGapL, topGapR, bottomGapR);
        // 中央付近に1個追加
        float trapRadius = ballRadius * 1.2f;
        float paddingY = trapRadius * 2.5f;
        float midX = (l + r) / 2f;
        float tx = midX - trapRadius + ((float)Math.random() - 0.5f) * ballRadius;
        float ty = t + paddingY + (float)Math.random() * (b - t - paddingY * 2);
        traps.add(new RectF(tx - trapRadius, ty - trapRadius, tx + trapRadius, ty + trapRadius));
    }

    private void addTrapsInSection(float l, float t, float r, float b, int count) {
        float trapRadius = ballRadius * 1.2f;
        float clearance = ballRadius * 2.5f; // ボールが通れる隙間を確保
        float padding = trapRadius + 10f;
        int maxAttempts = 100;

        for (int i = 0; i < count; i++) {
            boolean overlapping = true;
            float tx = 0, ty = 0;
            int attempts = 0;

            while (overlapping && attempts < maxAttempts) {
                tx = l + padding + (float) Math.random() * (r - l - padding * 2);
                ty = t + padding + (float) Math.random() * (b - t - padding * 2);
                
                // 当たり判定よりも広い範囲(checkRect)で重なりをチェックすることで隙間を作る
                RectF checkRect = new RectF(
                        tx - trapRadius - clearance / 2, ty - trapRadius - clearance / 2,
                        tx + trapRadius + clearance / 2, ty + trapRadius + clearance / 2
                );

                overlapping = false;
                // 他のトラップとの重なりチェック
                for (RectF existing : traps) {
                    if (RectF.intersects(existing, checkRect)) {
                        overlapping = true;
                        break;
                    }
                }
                if (overlapping) { attempts++; continue; }

                // 壁との重なりチェック
                for (RectF wall : walls) {
                    if (RectF.intersects(wall, checkRect)) {
                        overlapping = true;
                        break;
                    }
                }
                attempts++;
            }
            if (!overlapping) {
                traps.add(new RectF(tx - trapRadius, ty - trapRadius, tx + trapRadius, ty + trapRadius));
            }
        }
    }

    public void updateSensorValues(float ax, float ay) {
        if (isGameOver) return;

        ballVX -= ax * 0.5f;
        ballVY += ay * 0.5f;

        ballVX *= 0.98f;
        ballVY *= 0.98f;

        float nextX = ballX + ballVX;
        float nextY = ballY + ballVY;

        checkCollision(nextX, nextY);

        // トラップ判定 (球体の中心が穴に入ったらリセット)
        for (RectF trap : traps) {
            if (trap.contains(ballX, ballY)) {
                resetBall();
                break;
            }
        }
        
        if (RectF.intersects(goal, new RectF(ballX - ballRadius, ballY - ballRadius, ballX + ballRadius, ballY + ballRadius))) {
            if (!isGameOver) {
                isGameOver = true;
                endTime = System.currentTimeMillis();
                if (gameListener != null) {
                    gameListener.onGoal(getTimeString());
                }
            }
        }

        invalidate();
    }

    private String getTimeString() {
        long current = isGameOver ? endTime : System.currentTimeMillis();
        long diff = current - startTime;
        int seconds = (int) (diff / 1000);
        int millis = (int) (diff % 1000);
        return String.format(Locale.getDefault(), "%02d:%03d", seconds, millis);
    }

    private void resetBall() {
        ballX = ballRadius * 2.5f;
        ballY = ballRadius * 2.5f;
        ballVX = 0;
        ballVY = 0;
    }

    private void checkCollision(float nextX, float nextY) {
        boolean collidedX = false;
        boolean collidedY = false;
        RectF ballRectX = new RectF(nextX - ballRadius, ballY - ballRadius, nextX + ballRadius, ballY + ballRadius);
        RectF ballRectY = new RectF(ballX - ballRadius, nextY - ballRadius, ballX + ballRadius, nextY + ballRadius);

        for (RectF wall : walls) {
            if (RectF.intersects(wall, ballRectX)) {
                collidedX = true;
                ballVX = 0;
            }
            if (RectF.intersects(wall, ballRectY)) {
                collidedY = true;
                ballVY = 0;
            }
        }
        if (!collidedX) ballX = nextX;
        if (!collidedY) ballY = nextY;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.LTGRAY);
        canvas.drawRect(goal, goalPaint);

        for (RectF wall : walls) {
            canvas.drawRect(wall, wallPaint);
        }

        float trapRadius = ballRadius * 1.2f;
        for (RectF trap : traps) {
            canvas.drawCircle(trap.centerX(), trap.centerY(), trapRadius, trapPaint);
        }

        RadialGradient gradient = new RadialGradient(
                ballX - ballRadius * 0.3f,
                ballY - ballRadius * 0.3f,
                ballRadius * 1.5f,
                new int[]{Color.WHITE, Color.RED, Color.BLACK},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP
        );
        ballPaint.setShader(gradient);
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);

        // タイマー表示
        canvas.drawText("TIME: " + getTimeString(), 40, 70, timerPaint);

        if (isGameOver) {
            canvas.drawText("GOAL!!", getWidth() / 2f, getHeight() / 2f, textPaint);
        }
    }
}
