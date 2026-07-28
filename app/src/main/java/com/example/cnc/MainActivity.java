package com.example.cnc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvCoordinates;
    private TextView tvStatus;
    private EditText etGcode;
    private CncCanvasView cncCanvas;

    private Button btnStart, btnPause, btnReset, btnStop, btnSendGcode;
    private Button btnJogXPlus, btnJogXMinus, btnJogYPlus, btnJogYMinus, btnJogZPlus, btnJogZMinus, btnHome;

    private float posX = 0f;
    private float posY = 0f;
    private float posZ = 0f;

    private float targetX = 0f;
    private float targetY = 0f;
    private float targetZ = 0f;

    private boolean isRunning = false;
    private boolean isPaused = false;

    private final List<GCodeCommand> gcodeQueue = new ArrayList<>();
    private int currentCommandIndex = 0;

    private Handler animationHandler;
    private Runnable animationRunnable;

    private static class GCodeCommand {
        float x, y, z;
        String raw;

        GCodeCommand(float x, float y, float z, String raw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.raw = raw;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        setupListeners();
        setupSimulationEngine();

        updateStatusDisplay("IDLE");
        updateCoordinateDisplay();
    }

    private void initUI() {
        tvCoordinates = findViewById(R.id.tvCoordinates);
        tvStatus = findViewById(R.id.tvStatus);
        etGcode = findViewById(R.id.etGcode);
        cncCanvas = findViewById(R.id.cncCanvas);

        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnReset = findViewById(R.id.btnReset);
        btnStop = findViewById(R.id.btnStop);
        btnSendGcode = findViewById(R.id.btnSendGcode);

        btnJogXPlus = findViewById(R.id.btnJogXPlus);
        btnJogXMinus = findViewById(R.id.btnJogXMinus);
        btnJogYPlus = findViewById(R.id.btnJogYPlus);
        btnJogYMinus = findViewById(R.id.btnJogYMinus);
        btnJogZPlus = findViewById(R.id.btnJogZPlus);
        btnJogZMinus = findViewById(R.id.btnJogZMinus);
        btnHome = findViewById(R.id.btnHome);
    }

    private void setupListeners() {
        btnSendGcode.setOnClickListener(v -> parseAndEnqueueGcode());

        btnStart.setOnClickListener(v -> {
            if (gcodeQueue.isEmpty()) {
                // Add default demo shape if queue empty
                cncCanvas.clearPaths();
                gcodeQueue.add(new GCodeCommand(0, 0, 0, "G0 X0 Y0"));
                gcodeQueue.add(new GCodeCommand(60, 0, -2, "G1 X60 Y0 Z-2"));
                gcodeQueue.add(new GCodeCommand(60, 60, -2, "G1 X60 Y60 Z-2"));
                gcodeQueue.add(new GCodeCommand(0, 60, -2, "G1 X0 Y60 Z-2"));
                gcodeQueue.add(new GCodeCommand(0, 0, 0, "G0 X0 Y0 Z0"));

                for (GCodeCommand cmd : gcodeQueue) {
                    cncCanvas.addPoint(cmd.x, cmd.y);
                }
            }

            isRunning = true;
            isPaused = false;
            updateStatusDisplay("RUNNING");
        });

        btnPause.setOnClickListener(v -> {
            if (isRunning) {
                isPaused = !isPaused;
                updateStatusDisplay(isPaused ? "PAUSED" : "RUNNING");
            }
        });

        btnReset.setOnClickListener(v -> resetMachine());

        btnStop.setOnClickListener(v -> {
            isRunning = false;
            isPaused = false;
            updateStatusDisplay("E-STOPPED");
            Toast.makeText(MainActivity.this, "Emergency Stop Activated!", Toast.LENGTH_SHORT).show();
        });

        // Jog Controls
        btnJogXPlus.setOnClickListener(v -> jog(10f, 0f, 0f));
        btnJogXMinus.setOnClickListener(v -> jog(-10f, 0f, 0f));
        btnJogYPlus.setOnClickListener(v -> jog(0f, 10f, 0f));
        btnJogYMinus.setOnClickListener(v -> jog(0f, -10f, 0f));
        btnJogZPlus.setOnClickListener(v -> jog(0f, 0f, 2f));
        btnJogZMinus.setOnClickListener(v -> jog(0f, 0f, -2f));

        btnHome.setOnClickListener(v -> {
            posX = 0f;
            posY = 0f;
            posZ = 0f;
            targetX = 0f;
            targetY = 0f;
            targetZ = 0f;
            cncCanvas.setToolPosition(0, 0, 0);
            updateCoordinateDisplay();
            updateStatusDisplay("HOMED (0,0,0)");
        });
    }

    private void parseAndEnqueueGcode() {
        String input = etGcode.getText().toString().trim();
        if (input.isEmpty()) return;

        String[] lines = input.split("\n");
        for (String line : lines) {
            line = line.trim().toUpperCase();
            if (line.isEmpty() || line.startsWith(";")) continue;

            float x = targetX;
            float y = targetY;
            float z = targetZ;

            String[] tokens = line.split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("X")) {
                    try { x = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("Y")) {
                    try { y = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("Z")) {
                    try { z = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                }
            }

            gcodeQueue.add(new GCodeCommand(x, y, z, line));
            cncCanvas.addPoint(x, y);
            targetX = x;
            targetY = y;
            targetZ = z;
        }

        etGcode.setText("");
        Toast.makeText(this, "Enqueued G-Code Commands", Toast.LENGTH_SHORT).show();
    }

    private void jog(float dx, float dy, float dz) {
        if (isRunning) return;
        posX += dx;
        posY += dy;
        posZ += dz;
        cncCanvas.setToolPosition(posX, posY, posZ);
        updateCoordinateDisplay();
    }

    private void resetMachine() {
        isRunning = false;
        isPaused = false;
        currentCommandIndex = 0;
        gcodeQueue.clear();
        posX = 0f;
        posY = 0f;
        posZ = 0f;
        targetX = 0f;
        targetY = 0f;
        targetZ = 0f;
        cncCanvas.clearPaths();
        updateCoordinateDisplay();
        updateStatusDisplay("IDLE");
    }

    private void setupSimulationEngine() {
        animationHandler = new Handler(Looper.getMainLooper());
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused && !gcodeQueue.isEmpty() && currentCommandIndex < gcodeQueue.size()) {
                    GCodeCommand target = gcodeQueue.get(currentCommandIndex);

                    float speed = 1.5f; // mm per tick
                    float dx = target.x - posX;
                    float dy = target.y - posY;
                    float dz = target.z - posZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist <= speed) {
                        posX = target.x;
                        posY = target.y;
                        posZ = target.z;
                        currentCommandIndex++;

                        if (currentCommandIndex >= gcodeQueue.size()) {
                            isRunning = false;
                            updateStatusDisplay("COMPLETED");
                        }
                    } else {
                        posX += (dx / dist) * speed;
                        posY += (dy / dist) * speed;
                        posZ += (dz / dist) * speed;
                    }

                    cncCanvas.setToolPosition(posX, posY, posZ);
                    updateCoordinateDisplay();
                }

                animationHandler.postDelayed(this, 30);
            }
        };
        animationHandler.post(animationRunnable);
    }

    private void updateCoordinateDisplay() {
        tvCoordinates.setText(String.format(Locale.US, "X: %.2f  Y: %.2f  Z: %.2f", posX, posY, posZ));
    }

    private void updateStatusDisplay(String status) {
        tvStatus.setText("STATUS: " + status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
        }
    }
}
