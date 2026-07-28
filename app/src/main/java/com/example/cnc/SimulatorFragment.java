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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private String activeGcode = null;

    private static class ParsedCommand {
        CncCanvasView.MotionType type;
        CncCanvasView.WorkPlane plane;
        float x, y, z;
        float i, j, k;
        float feedRate;
        String raw;

        ParsedCommand(CncCanvasView.MotionType type, CncCanvasView.WorkPlane plane,
                      float x, float y, float z, float i, float j, float k, float feedRate, String raw) {
            this.type = type;
            this.plane = plane;
            this.x = x;
            this.y = y;
            this.z = z;
            this.i = i;
            this.j = j;
            this.k = k;
            this.feedRate = feedRate;
            this.raw = raw;
        }
    }

    private final List<ParsedCommand> commandList = new ArrayList<>();
    private int currentCmdIndex = 0;

    private Handler animationHandler;
    private Runnable animationRunnable;

    public void setPendingGcode(String gcode) {
        if (gcode == null || gcode.trim().isEmpty()) return;
        this.activeGcode = gcode;
        if (etGcode != null) {
            etGcode.setText(""); // Complete clear
            etGcode.setText(gcode); // Overwrite with new sample Gcode
            parseAndLoadGcode(gcode);
            isRunning = true;
            isPaused = false;
            updateStatusDisplay("RUNNING");
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

        if (activeGcode != null) {
            etGcode.setText(activeGcode);
            parseAndLoadGcode(activeGcode);
        } else {
            // Default demo square + circle
            String demoGcode = "G21 G90 G17\nG00 X0 Y0 Z5\nG01 Z-2 F150\nG01 X50 Y0 F300\nG01 X50 Y50 F300\nG02 X0 Y50 I-25 J0 F200\nG01 X0 Y0 F300\nG00 Z10\nM30";
            activeGcode = demoGcode;
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
                activeGcode = input;
                parseAndLoadGcode(input);
                isRunning = true;
                isPaused = false;
                updateStatusDisplay("RUNNING");
                Toast.makeText(getContext(), "شبیه‌سازی G-Code آغاز شد", Toast.LENGTH_SHORT).show();
            }
        });

        btnStart.setOnClickListener(v -> {
            String input = etGcode.getText().toString().trim();
            if (!input.isEmpty()) {
                if (commandList.isEmpty() || !input.equals(activeGcode) || currentCmdIndex >= commandList.size()) {
                    activeGcode = input;
                    parseAndLoadGcode(input);
                }
                isRunning = true;
                isPaused = false;
                updateStatusDisplay("RUNNING");
            }
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

        if (input == null || input.trim().isEmpty()) return;

        float curX = 0f, curY = 0f, curZ = 0f;
        CncCanvasView.MotionType currentMotion = CncCanvasView.MotionType.LINEAR_G01;
        CncCanvasView.WorkPlane currentPlane = CncCanvasView.WorkPlane.XY_G17;
        float currentFeed = 200f; // Default F200 feed rate

        // Compiled Regex Patterns for Precise Keyword Parsing
        Pattern patternG = Pattern.compile("G0*([0-3]|17|18|19)\\b", Pattern.CASE_INSENSITIVE);
        Pattern patternF = Pattern.compile("F\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternX = Pattern.compile("X\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternY = Pattern.compile("Y\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternZ = Pattern.compile("Z\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternI = Pattern.compile("I\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternJ = Pattern.compile("J\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternK = Pattern.compile("K\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);
        Pattern patternR = Pattern.compile("R\\s*([-+]?\\d*\\.?\\d+)", Pattern.CASE_INSENSITIVE);

        String[] lines = input.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.replaceAll(";.*|\\(.*\\)", "").trim().toUpperCase();
            if (line.isEmpty()) continue;

            // 1. Detect Motion Type & Plane (Modal)
            Matcher mG = patternG.matcher(line);
            while (mG.find()) {
                String code = mG.group(1);
                if ("0".equals(code) || "00".equals(code)) currentMotion = CncCanvasView.MotionType.RAPID_G00;
                else if ("1".equals(code) || "01".equals(code)) currentMotion = CncCanvasView.MotionType.LINEAR_G01;
                else if ("2".equals(code) || "02".equals(code)) currentMotion = CncCanvasView.MotionType.ARC_CW_G02;
                else if ("3".equals(code) || "03".equals(code)) currentMotion = CncCanvasView.MotionType.ARC_CCW_G03;
                else if ("17".equals(code)) currentPlane = CncCanvasView.WorkPlane.XY_G17;
                else if ("18".equals(code)) currentPlane = CncCanvasView.WorkPlane.XZ_G18;
                else if ("19".equals(code)) currentPlane = CncCanvasView.WorkPlane.YZ_G19;
            }

            // 2. Feed Rate F
            Matcher mF = patternF.matcher(line);
            if (mF.find()) {
                try { currentFeed = Float.parseFloat(mF.group(1)); } catch (Exception ignored) {}
            }

            // 3. Extract Target Coordinates (Preserve previous position if omitted)
            float targetX = curX;
            float targetY = curY;
            float targetZ = curZ;

            Matcher mX = patternX.matcher(line);
            if (mX.find()) { try { targetX = Float.parseFloat(mX.group(1)); } catch (Exception ignored) {} }

            Matcher mY = patternY.matcher(line);
            if (mY.find()) { try { targetY = Float.parseFloat(mY.group(1)); } catch (Exception ignored) {} }

            Matcher mZ = patternZ.matcher(line);
            if (mZ.find()) { try { targetZ = Float.parseFloat(mZ.group(1)); } catch (Exception ignored) {} }

            // 4. Extract Arc Parameters I, J, K, R
            float offsetI = 0f, offsetJ = 0f, offsetK = 0f, radiusR = 0f;

            Matcher mI = patternI.matcher(line);
            if (mI.find()) { try { offsetI = Float.parseFloat(mI.group(1)); } catch (Exception ignored) {} }

            Matcher mJ = patternJ.matcher(line);
            if (mJ.find()) { try { offsetJ = Float.parseFloat(mJ.group(1)); } catch (Exception ignored) {} }

            Matcher mK = patternK.matcher(line);
            if (mK.find()) { try { offsetK = Float.parseFloat(mK.group(1)); } catch (Exception ignored) {} }

            Matcher mR = patternR.matcher(line);
            if (mR.find()) { try { radiusR = Float.parseFloat(mR.group(1)); } catch (Exception ignored) {} }

            // Radius R calculation if I, J, K are not explicitly set for arcs
            if (radiusR != 0f && offsetI == 0f && offsetJ == 0f && offsetK == 0f &&
                    (currentMotion == CncCanvasView.MotionType.ARC_CW_G02 || currentMotion == CncCanvasView.MotionType.ARC_CCW_G03)) {
                if (currentPlane == CncCanvasView.WorkPlane.XZ_G18) {
                    float dx = targetX - curX;
                    float dz = targetZ - curZ;
                    float dist = (float) Math.hypot(dx, dz);
                    if (dist > 0 && dist <= 2 * Math.abs(radiusR)) {
                        float h = (float) Math.sqrt(Math.max(0, radiusR * radiusR - (dist / 2f) * (dist / 2f)));
                        float mx = (curX + targetX) / 2f;
                        float mz = (curZ + targetZ) / 2f;
                        float sign = (currentMotion == CncCanvasView.MotionType.ARC_CW_G02) ? -1f : 1f;
                        if (radiusR < 0) sign = -sign;
                        float cx = mx + sign * h * (-dz / dist);
                        float cz = mz + sign * h * (dx / dist);
                        offsetI = cx - curX;
                        offsetK = cz - curZ;
                    }
                } else if (currentPlane == CncCanvasView.WorkPlane.YZ_G19) {
                    float dy = targetY - curY;
                    float dz = targetZ - curZ;
                    float dist = (float) Math.hypot(dy, dz);
                    if (dist > 0 && dist <= 2 * Math.abs(radiusR)) {
                        float h = (float) Math.sqrt(Math.max(0, radiusR * radiusR - (dist / 2f) * (dist / 2f)));
                        float my = (curY + targetY) / 2f;
                        float mz = (curZ + targetZ) / 2f;
                        float sign = (currentMotion == CncCanvasView.MotionType.ARC_CW_G02) ? -1f : 1f;
                        if (radiusR < 0) sign = -sign;
                        float cy = my + sign * h * (-dz / dist);
                        float cz = mz + sign * h * (dy / dist);
                        offsetJ = cy - curY;
                        offsetK = cz - curZ;
                    }
                } else { // G17 XY
                    float dx = targetX - curX;
                    float dy = targetY - curY;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist > 0 && dist <= 2 * Math.abs(radiusR)) {
                        float h = (float) Math.sqrt(Math.max(0, radiusR * radiusR - (dist / 2f) * (dist / 2f)));
                        float mx = (curX + targetX) / 2f;
                        float my = (curY + targetY) / 2f;
                        float sign = (currentMotion == CncCanvasView.MotionType.ARC_CW_G02) ? -1f : 1f;
                        if (radiusR < 0) sign = -sign;
                        float cx = mx + sign * h * (-dy / dist);
                        float cy = my + sign * h * (dx / dist);
                        offsetI = cx - curX;
                        offsetJ = cy - curY;
                    }
                }
            }

            // Add segment if position changed or arc configured
            boolean positionChanged = (targetX != curX || targetY != curY || targetZ != curZ || offsetI != 0f || offsetJ != 0f || offsetK != 0f);
            if (positionChanged) {
                CncCanvasView.ToolSegment segment = new CncCanvasView.ToolSegment(
                        currentMotion, currentPlane, curX, curY, curZ, targetX, targetY, targetZ,
                        offsetI, offsetJ, offsetK, currentFeed
                );
                cncCanvas.addSegment(segment);
                commandList.add(new ParsedCommand(
                        currentMotion, currentPlane, targetX, targetY, targetZ,
                        offsetI, offsetJ, offsetK, currentFeed, rawLine
                ));

                curX = targetX;
                curY = targetY;
                curZ = targetZ;
            }
        }

        cncCanvas.recalculateScaleAndBounds();
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

                    float speed = (target.type == CncCanvasView.MotionType.RAPID_G00) ? 5.0f :
                            Math.max(0.8f, Math.min(6.0f, target.feedRate / 80f));

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
