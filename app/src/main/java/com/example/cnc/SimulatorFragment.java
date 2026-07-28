package com.example.cnc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimulatorFragment extends Fragment {

    private TextView tvCoordinates;
    private TextView tvStatus;
    private EditText etGcode;
    private CncCanvasView cncCanvas;

    private Button btnStart, btnPause, btnReset, btnStop, btnSendGcode;
    private Button btnJogXPlus, btnJogXMinus, btnJogYPlus, btnJogYMinus, btnJogZPlus, btnJogZMinus, btnHome;

    private float posX = 0f;
    private float posY = 0f;
    private float posZ = 0f;

    private boolean isRunning = false;
    private boolean isPaused = false;

    private String pendingGcode = null;

    private static class ParsedCommand {
        CncCanvasView.MotionType type;
        float x, y, z;
        float i, j;
        String raw;

        ParsedCommand(CncCanvasView.MotionType type, float x, float y, float z, float i, float j, String raw) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.i = i;
            this.j = j;
            this.raw = raw;
        }
    }

    private final List<ParsedCommand> commandList = new ArrayList<>();
    private int currentCmdIndex = 0;

    private Handler animationHandler;
    private Runnable animationRunnable;

    public void setPendingGcode(String gcode) {
        this.pendingGcode = gcode;
        if (etGcode != null && gcode != null) {
            etGcode.setText(gcode);
            parseAndLoadGcode(gcode);
            pendingGcode = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_simulator, container, false);

        tvCoordinates = view.findViewById(R.id.tvCoordinates);
        tvStatus = view.findViewById(R.id.tvStatus);
        etGcode = view.findViewById(R.id.etGcode);
        cncCanvas = view.findViewById(R.id.cncCanvas);

        btnStart = view.findViewById(R.id.btnStart);
        btnPause = view.findViewById(R.id.btnPause);
        btnReset = view.findViewById(R.id.btnReset);
        btnStop = view.findViewById(R.id.btnStop);
        btnSendGcode = view.findViewById(R.id.btnSendGcode);

        btnJogXPlus = view.findViewById(R.id.btnJogXPlus);
        btnJogXMinus = view.findViewById(R.id.btnJogXMinus);
        btnJogYPlus = view.findViewById(R.id.btnJogYPlus);
        btnJogYMinus = view.findViewById(R.id.btnJogYMinus);
        btnJogZPlus = view.findViewById(R.id.btnJogZPlus);
        btnJogZMinus = view.findViewById(R.id.btnJogZMinus);
        btnHome = view.findViewById(R.id.btnHome);

        setupListeners();
        setupSimulationEngine();

        if (pendingGcode != null) {
            etGcode.setText(pendingGcode);
            parseAndLoadGcode(pendingGcode);
            pendingGcode = null;
        } else {
            // Default demo square + circle
            String demoGcode = "G21 G90 G17\nG00 X0 Y0 Z5\nG01 Z-2 F150\nG01 X50 Y0 F300\nG01 X50 Y50 F300\nG02 X0 Y50 I-25 J0 F200\nG01 X0 Y0 F300\nG00 Z10\nM30";
            etGcode.setText(demoGcode);
            parseAndLoadGcode(demoGcode);
        }

        updateStatusDisplay("IDLE");
        updateCoordinateDisplay();

        return view;
    }

    private void setupListeners() {
        btnSendGcode.setOnClickListener(v -> {
            String input = etGcode.getText().toString().trim();
            if (!input.isEmpty()) {
                parseAndLoadGcode(input);
                Toast.makeText(getContext(), "G-Code بارگذاری شد", Toast.LENGTH_SHORT).show();
            }
        });

        btnStart.setOnClickListener(v -> {
            if (commandList.isEmpty()) {
                parseAndLoadGcode(etGcode.getText().toString());
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
            Toast.makeText(getContext(), "توقف اضطراری اسپیندل و محورها!", Toast.LENGTH_SHORT).show();
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
            cncCanvas.setToolPosition(0, 0, 0);
            updateCoordinateDisplay();
            updateStatusDisplay("HOMED (0,0,0)");
        });
    }

    private void jog(float dx, float dy, float dz) {
        if (isRunning) return;
        posX += dx;
        posY += dy;
        posZ += dz;
        cncCanvas.setToolPosition(posX, posY, posZ);
        updateCoordinateDisplay();
    }

    private void parseAndLoadGcode(String input) {
        cncCanvas.clearAll();
        commandList.clear();
        currentCmdIndex = 0;

        float curX = 0f, curY = 0f, curZ = 0f;
        CncCanvasView.MotionType currentMode = CncCanvasView.MotionType.LINEAR_G01;

        String[] lines = input.split("\n");
        for (String line : lines) {
            line = line.trim().toUpperCase();
            if (line.isEmpty() || line.startsWith(";")) continue;

            if (line.contains("G00") || line.contains("G0 ")) {
                currentMode = CncCanvasView.MotionType.RAPID_G00;
            } else if (line.contains("G01") || line.contains("G1 ")) {
                currentMode = CncCanvasView.MotionType.LINEAR_G01;
            } else if (line.contains("G02") || line.contains("G2 ")) {
                currentMode = CncCanvasView.MotionType.ARC_CW_G02;
            } else if (line.contains("G03") || line.contains("G3 ")) {
                currentMode = CncCanvasView.MotionType.ARC_CCW_G03;
            }

            float targetX = curX;
            float targetY = curY;
            float targetZ = curZ;
            float offsetI = 0f;
            float offsetJ = 0f;

            String[] tokens = line.split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("X")) {
                    try { targetX = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("Y")) {
                    try { targetY = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("Z")) {
                    try { targetZ = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("I")) {
                    try { offsetI = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                } else if (token.startsWith("J")) {
                    try { offsetJ = Float.parseFloat(token.substring(1)); } catch (Exception ignored) {}
                }
            }

            cncCanvas.addSegment(new CncCanvasView.ToolSegment(
                    currentMode, curX, curY, curZ, targetX, targetY, targetZ, offsetI, offsetJ
            ));

            commandList.add(new ParsedCommand(currentMode, targetX, targetY, targetZ, offsetI, offsetJ, line));

            curX = targetX;
            curY = targetY;
            curZ = targetZ;
        }

        posX = 0f;
        posY = 0f;
        posZ = 0f;
        cncCanvas.setToolPosition(0, 0, 0);
    }

    private void resetMachine() {
        isRunning = false;
        isPaused = false;
        currentCmdIndex = 0;
        posX = 0f;
        posY = 0f;
        posZ = 0f;
        if (etGcode != null) {
            parseAndLoadGcode(etGcode.getText().toString());
        }
        updateCoordinateDisplay();
        updateStatusDisplay("IDLE");
    }

    private void setupSimulationEngine() {
        animationHandler = new Handler(Looper.getMainLooper());
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused && !commandList.isEmpty() && currentCmdIndex < commandList.size()) {
                    ParsedCommand target = commandList.get(currentCmdIndex);

                    float speed = (target.type == CncCanvasView.MotionType.RAPID_G00) ? 3.0f : 1.5f;

                    float dx = target.x - posX;
                    float dy = target.y - posY;
                    float dz = target.z - posZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist <= speed) {
                        posX = target.x;
                        posY = target.y;
                        posZ = target.z;
                        currentCmdIndex++;

                        if (currentCmdIndex >= commandList.size()) {
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
        if (tvCoordinates != null) {
            tvCoordinates.setText(String.format(Locale.US, "X: %.2f  Y: %.2f  Z: %.2f", posX, posY, posZ));
        }
    }

    private void updateStatusDisplay(String status) {
        if (tvStatus != null) {
            tvStatus.setText("STATUS: " + status);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
        }
    }
}
