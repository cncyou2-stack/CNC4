package com.example.cnc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Advanced 3D CNC Viewport Canvas.
 * Provides interactive 3D perspective projection, 3D coordinate axes (X,Y,Z),
 * 3D spindle & tool head, 3D workpiece stock bed, and G00/G01/G02/G03 path visualization.
 */
public class CncCanvasView extends View {

    public enum MotionType {
        RAPID_G00,
        LINEAR_G01,
        ARC_CW_G02,
        ARC_CCW_G03
    }

    public static class ToolSegment {
        public MotionType type;
        public float startX, startY, startZ;
        public float endX, endY, endZ;
        public float iOffset, jOffset;

        public ToolSegment(MotionType type, float startX, float startY, float startZ,
                           float endX, float endY, float endZ, float iOffset, float jOffset) {
            this.type = type;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.iOffset = iOffset;
            this.jOffset = jOffset;
        }
    }

    // 3D Camera & Viewport settings
    private float rotX = 35.0f; // Pitch angle in degrees
    private float rotZ = -45.0f; // Yaw angle in degrees
    private float scale = 5.0f; // Scale factor px/mm
    private float panX = 0f;
    private float panY = 0f;

    private float currentX = 0f;
    private float currentY = 0f;
    private float currentZ = 0f;

    private float minX = 0f, maxX = 60f;
    private float minY = 0f, maxY = 60f;
    private float minZ = -10f, maxZ = 20f;

    private final List<ToolSegment> segments = new ArrayList<>();
    private final List<float[]> executedPoints = new ArrayList<>();

    // Touch gesture handling
    private ScaleGestureDetector scaleDetector;
    private GestureDetector doubleTapDetector;
    private float lastTouchX, lastTouchY;
    private int activePointerCount = 0;

    // Paints
    private Paint grid3dPaint;
    private Paint stockBedPaint;
    private Paint stockWirePaint;

    private Paint axisXPaint;
    private Paint axisYPaint;
    private Paint axisZPaint;

    private Paint rapidPaint;
    private Paint linearCutPaint;
    private Paint depthCutPaint;
    private Paint arcCwPaint;
    private Paint arcCcwPaint;

    private Paint shadowPathPaint;
    private Paint executedPathPaint;

    private Paint spindleBodyPaint;
    private Paint spindleCollarPaint;
    private Paint toolBitPaint;
    private Paint zDepthLinePaint;

    private Paint hudBgPaint;
    private Paint textWhitePaint;
    private Paint textMutedPaint;

    public CncCanvasView(Context context) {
        super(context);
        init(context);
    }

    public CncCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CncCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        grid3dPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid3dPaint.setColor(Color.parseColor("#334155"));
        grid3dPaint.setStrokeWidth(1.2f);
        grid3dPaint.setStyle(Paint.Style.STROKE);

        stockBedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stockBedPaint.setColor(Color.parseColor("#1E293B"));
        stockBedPaint.setStyle(Paint.Style.FILL);

        stockWirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stockWirePaint.setColor(Color.parseColor("#475569"));
        stockWirePaint.setStrokeWidth(2.0f);
        stockWirePaint.setStyle(Paint.Style.STROKE);

        // 3D Coordinate Axes
        axisXPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisXPaint.setColor(Color.parseColor("#EF4444")); // Red for X Axis
        axisXPaint.setStrokeWidth(4.5f);

        axisYPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisYPaint.setColor(Color.parseColor("#22C55E")); // Green for Y Axis
        axisYPaint.setStrokeWidth(4.5f);

        axisZPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisZPaint.setColor(Color.parseColor("#3B82F6")); // Blue for Z Axis
        axisZPaint.setStrokeWidth(4.5f);

        // Motion Paths
        rapidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rapidPaint.setColor(Color.parseColor("#F87171")); // Dashed Coral
        rapidPaint.setStrokeWidth(2.5f);
        rapidPaint.setStyle(Paint.Style.STROKE);
        rapidPaint.setPathEffect(new DashPathEffect(new float[]{10, 8}, 0));

        linearCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linearCutPaint.setColor(Color.parseColor("#38BDF8")); // Cyan
        linearCutPaint.setStrokeWidth(4.5f);
        linearCutPaint.setStyle(Paint.Style.STROKE);

        depthCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        depthCutPaint.setColor(Color.parseColor("#F43F5E")); // Vibrant Red for Z < 0 cutting
        depthCutPaint.setStrokeWidth(6.5f);
        depthCutPaint.setStyle(Paint.Style.STROKE);

        arcCwPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcCwPaint.setColor(Color.parseColor("#F59E0B")); // Amber
        arcCwPaint.setStrokeWidth(5.0f);
        arcCwPaint.setStyle(Paint.Style.STROKE);

        arcCcwPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcCcwPaint.setColor(Color.parseColor("#EC4899")); // Pink
        arcCcwPaint.setStrokeWidth(5.0f);
        arcCcwPaint.setStyle(Paint.Style.STROKE);

        shadowPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPathPaint.setColor(Color.parseColor("#0F172A"));
        shadowPathPaint.setStrokeWidth(3.0f);
        shadowPathPaint.setStyle(Paint.Style.STROKE);

        executedPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        executedPathPaint.setColor(Color.parseColor("#A855F7")); // Purple executed path
        executedPathPaint.setStrokeWidth(6.0f);
        executedPathPaint.setStyle(Paint.Style.STROKE);

        // Spindle & Tool
        spindleBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spindleBodyPaint.setColor(Color.parseColor("#94A3B8"));
        spindleBodyPaint.setStyle(Paint.Style.FILL);

        spindleCollarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spindleCollarPaint.setColor(Color.parseColor("#475569"));
        spindleCollarPaint.setStyle(Paint.Style.FILL);

        toolBitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        toolBitPaint.setColor(Color.parseColor("#FBBF24")); // Tungsten Gold Bit
        toolBitPaint.setStyle(Paint.Style.FILL);

        zDepthLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zDepthLinePaint.setColor(Color.parseColor("#E2E8F0"));
        zDepthLinePaint.setStrokeWidth(2.5f);
        zDepthLinePaint.setStyle(Paint.Style.STROKE);
        zDepthLinePaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));

        // HUD & Legends
        hudBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudBgPaint.setColor(Color.parseColor("#0F172A"));
        hudBgPaint.setAlpha(220);

        textWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textWhitePaint.setColor(Color.parseColor("#F8FAFC"));
        textWhitePaint.setTextSize(22f);

        textMutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textMutedPaint.setColor(Color.parseColor("#94A3B8"));
        textMutedPaint.setTextSize(18f);

        // Scale gesture detector
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                scale *= scaleFactor;
                scale = Math.max(0.5f, Math.min(scale, 50.0f));
                invalidate();
                return true;
            }
        });

        // Double tap detector to reset camera
        doubleTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetCameraView();
                return true;
            }
        });
    }

    public void resetCameraView() {
        rotX = 35.0f;
        rotZ = -45.0f;
        panX = 0f;
        panY = 0f;
        recalculateScaleAndBounds();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        doubleTapDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        activePointerCount = event.getPointerCount();

        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;

                    if (activePointerCount == 1) {
                        // 1 Finger: Orbit 3D scene (Rotate X and Z)
                        rotZ += dx * 0.45f;
                        rotX -= dy * 0.45f;
                        rotX = Math.max(5.0f, Math.min(rotX, 85.0f)); // Clamp tilt angle
                    } else if (activePointerCount >= 2) {
                        // 2 Fingers: Pan 3D Viewport
                        panX += dx;
                        panY += dy;
                    }

                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                }
                break;
        }
        return true;
    }

    public void setToolPosition(float x, float y, float z) {
        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;

        executedPoints.add(new float[]{x, y, z});
        invalidate();
    }

    public void addSegment(ToolSegment segment) {
        segments.add(segment);
        invalidate();
    }

    public void clearAll() {
        segments.clear();
        executedPoints.clear();
        currentX = 0f;
        currentY = 0f;
        currentZ = 0f;
        recalculateScaleAndBounds();
        invalidate();
    }

    public void recalculateScaleAndBounds() {
        if (segments.isEmpty()) {
            minX = 0f; maxX = 50f;
            minY = 0f; maxY = 50f;
            minZ = -10f; maxZ = 20f;
        } else {
            minX = 0f; maxX = 10f;
            minY = 0f; maxY = 10f;
            minZ = -2f; maxZ = 10f;

            for (ToolSegment seg : segments) {
                minX = Math.min(minX, Math.min(seg.startX, seg.endX));
                maxX = Math.max(maxX, Math.max(seg.startX, seg.endX));
                minY = Math.min(minY, Math.min(seg.startY, seg.endY));
                maxY = Math.max(maxY, Math.max(seg.startY, seg.endY));
                minZ = Math.min(minZ, Math.min(seg.startZ, seg.endZ));
                maxZ = Math.max(maxZ, Math.max(seg.startZ, seg.endZ));
            }
        }

        float viewW = getWidth();
        float viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float contentW = Math.max(20f, maxX - minX);
        float contentH = Math.max(20f, maxY - minY);

        float availW = viewW * 0.7f;
        float availH = viewH * 0.7f;

        scale = Math.min(availW / contentW, availH / contentH);
        scale = Math.max(1.5f, Math.min(scale, 18.0f));

        invalidate();
    }

    /**
     * 3D Perspective & Isometric Projection Formula.
     * Maps 3D coordinates (x, y, z) in CNC space into 2D Screen (px, py).
     */
    private float[] project3D(float x, float y, float z) {
        float centerX = getWidth() / 2.0f + panX;
        float centerY = getHeight() / 2.0f + panY;

        // Center scene around workspace middle
        float sceneMidX = (minX + maxX) / 2.0f;
        float sceneMidY = (minY + maxY) / 2.0f;

        float relX = x - sceneMidX;
        float relY = y - sceneMidY;
        float relZ = z;

        // 1. Rotate around Z axis (Yaw rotZ)
        double radZ = Math.toRadians(rotZ);
        double x1 = relX * Math.cos(radZ) - relY * Math.sin(radZ);
        double y1 = relX * Math.sin(radZ) + relY * Math.cos(radZ);
        double z1 = relZ;

        // 2. Rotate around X axis (Pitch rotX)
        double radX = Math.toRadians(rotX);
        double x2 = x1;
        double y2 = y1 * Math.cos(radX) - z1 * Math.sin(radX);
        double z2 = y1 * Math.sin(radX) + z1 * Math.cos(radX);

        // Perspective factor
        float viewDist = 1200f;
        float perspective = (viewDist > 0) ? (float) (viewDist / (viewDist + y2)) : 1.0f;

        float screenX = (float) (centerX + (x2 * scale * perspective));
        float screenY = (float) (centerY - (z2 * scale * perspective));

        return new float[]{screenX, screenY};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 1. Draw 3D Material Stock Block & Workpiece Bed (Z = 0)
        draw3dStockBed(canvas);

        // 2. Draw 3D Coordinate System Axes (X, Y, Z)
        draw3dAxes(canvas);

        // 3. Draw Planned G-Code Toolpaths in 3D
        draw3dToolpaths(canvas);

        // 4. Draw Executed Trajectory
        draw3dExecutedPath(canvas);

        // 5. Draw 3D Spindle Assembly & Tool Head
        draw3dSpindleAndTool(canvas);

        // 6. Draw HUD & Controls Overlay
        drawHudOverlay(canvas, width, height);
    }

    private void draw3dStockBed(Canvas canvas) {
        float bedMinX = Math.min(-10f, minX - 10f);
        float bedMaxX = Math.max(60f, maxX + 10f);
        float bedMinY = Math.min(-10f, minY - 10f);
        float bedMaxY = Math.max(60f, maxY + 10f);

        float[] p1 = project3D(bedMinX, bedMinY, 0);
        float[] p2 = project3D(bedMaxX, bedMinY, 0);
        float[] p3 = project3D(bedMaxX, bedMaxY, 0);
        float[] p4 = project3D(bedMinX, bedMaxY, 0);

        // Draw Stock Top Surface Polygon
        Path bedPath = new Path();
        bedPath.moveTo(p1[0], p1[1]);
        bedPath.lineTo(p2[0], p2[1]);
        bedPath.lineTo(p3[0], p3[1]);
        bedPath.lineTo(p4[0], p4[1]);
        bedPath.close();

        canvas.drawPath(bedPath, stockBedPaint);
        canvas.drawPath(bedPath, stockWirePaint);

        // Draw 3D Stock Block Depth Sides (Z = -15mm thickness)
        float[] b1 = project3D(bedMinX, bedMinY, -15);
        float[] b2 = project3D(bedMaxX, bedMinY, -15);
        float[] b3 = project3D(bedMaxX, bedMaxY, -15);

        Path sidePath = new Path();
        sidePath.moveTo(p1[0], p1[1]);
        sidePath.lineTo(p2[0], p2[1]);
        sidePath.lineTo(b2[0], b2[1]);
        sidePath.lineTo(b1[0], b1[1]);
        sidePath.close();

        Paint sidePaint = new Paint(stockBedPaint);
        sidePaint.setColor(Color.parseColor("#0F172A"));
        canvas.drawPath(sidePath, sidePaint);
        canvas.drawPath(sidePath, stockWirePaint);

        // Grid lines on top bed surface
        float step = 10f; // 10mm grid
        for (float x = bedMinX; x <= bedMaxX; x += step) {
            float[] gp1 = project3D(x, bedMinY, 0);
            float[] gp2 = project3D(x, bedMaxY, 0);
            canvas.drawLine(gp1[0], gp1[1], gp2[0], gp2[1], grid3dPaint);
        }
        for (float y = bedMinY; y <= bedMaxY; y += step) {
            float[] gp1 = project3D(bedMinX, y, 0);
            float[] gp2 = project3D(bedMaxX, y, 0);
            canvas.drawLine(gp1[0], gp1[1], gp2[0], gp2[1], grid3dPaint);
        }
    }

    private void draw3dAxes(Canvas canvas) {
        float axisLen = 45f;

        float[] o = project3D(0, 0, 0);
        float[] x = project3D(axisLen, 0, 0);
        float[] y = project3D(0, axisLen, 0);
        float[] z = project3D(0, 0, axisLen);

        // X Axis (Red)
        canvas.drawLine(o[0], o[1], x[0], x[1], axisXPaint);
        canvas.drawCircle(x[0], x[1], 6f, axisXPaint);
        canvas.drawText("X+", x[0] + 10, x[1] + 5, axisXPaint);

        // Y Axis (Green)
        canvas.drawLine(o[0], o[1], y[0], y[1], axisYPaint);
        canvas.drawCircle(y[0], y[1], 6f, axisYPaint);
        canvas.drawText("Y+", y[0] + 10, y[1] + 5, axisYPaint);

        // Z Axis (Blue)
        canvas.drawLine(o[0], o[1], z[0], z[1], axisZPaint);
        canvas.drawCircle(z[0], z[1], 6f, axisZPaint);
        canvas.drawText("Z+", z[0] + 10, z[1] - 5, axisZPaint);

        // Origin Dot
        Paint oDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        oDotPaint.setColor(Color.WHITE);
        canvas.drawCircle(o[0], o[1], 8f, oDotPaint);
        canvas.drawText("(0,0,0)", o[0] - 30, o[1] + 25, textMutedPaint);
    }

    private void draw3dToolpaths(Canvas canvas) {
        for (ToolSegment seg : segments) {
            float[] pStart = project3D(seg.startX, seg.startY, seg.startZ);
            float[] pEnd = project3D(seg.endX, seg.endY, seg.endZ);

            // Project shadow on Z=0 stock bed
            float[] sStart = project3D(seg.startX, seg.startY, 0);
            float[] sEnd = project3D(seg.endX, seg.endY, 0);
            canvas.drawLine(sStart[0], sStart[1], sEnd[0], sEnd[1], shadowPathPaint);

            if (seg.type == MotionType.RAPID_G00) {
                canvas.drawLine(pStart[0], pStart[1], pEnd[0], pEnd[1], rapidPaint);
            } else if (seg.type == MotionType.LINEAR_G01) {
                // If Z < 0 (Cutting into material), draw prominent deep cut stroke
                if (seg.startZ < 0 || seg.endZ < 0) {
                    canvas.drawLine(pStart[0], pStart[1], pEnd[0], pEnd[1], depthCutPaint);
                } else {
                    canvas.drawLine(pStart[0], pStart[1], pEnd[0], pEnd[1], linearCutPaint);
                }
            } else if (seg.type == MotionType.ARC_CW_G02 || seg.type == MotionType.ARC_CCW_G03) {
                double cx = seg.startX + seg.iOffset;
                double cy = seg.startY + seg.jOffset;
                double radius = Math.hypot(seg.iOffset, seg.jOffset);

                if (radius < 1e-3) {
                    canvas.drawLine(pStart[0], pStart[1], pEnd[0], pEnd[1],
                            seg.type == MotionType.ARC_CW_G02 ? arcCwPaint : arcCcwPaint);
                } else {
                    double startAngle = Math.atan2(seg.startY - cy, seg.startX - cx);
                    double endAngle = Math.atan2(seg.endY - cy, seg.endX - cx);
                    double sweep = endAngle - startAngle;

                    if (seg.type == MotionType.ARC_CW_G02) {
                        if (sweep >= 0) sweep -= 2 * Math.PI;
                    } else {
                        if (sweep <= 0) sweep += 2 * Math.PI;
                    }

                    int steps = Math.max(20, (int) (Math.abs(sweep) * 20));
                    Paint arcPaint = (seg.type == MotionType.ARC_CW_G02) ? arcCwPaint : arcCcwPaint;

                    float prevX = pStart[0];
                    float prevY = pStart[1];

                    for (int i = 1; i <= steps; i++) {
                        double angle = startAngle + (sweep * i / steps);
                        float curMmX = (float) (cx + radius * Math.cos(angle));
                        float curMmY = (float) (cy + radius * Math.sin(angle));
                        float curMmZ = seg.startX + (seg.endZ - seg.startZ) * i / steps;

                        float[] pCur = project3D(curMmX, curMmY, curMmZ);
                        canvas.drawLine(prevX, prevY, pCur[0], pCur[1], arcPaint);

                        prevX = pCur[0];
                        prevY = pCur[1];
                    }
                }
            }
        }
    }

    private void draw3dExecutedPath(Canvas canvas) {
        if (executedPoints.size() > 1) {
            Path path = new Path();
            float[] p0 = project3D(executedPoints.get(0)[0], executedPoints.get(0)[1], executedPoints.get(0)[2]);
            path.moveTo(p0[0], p0[1]);

            for (int i = 1; i < executedPoints.size(); i++) {
                float[] p = executedPoints.get(i);
                float[] pProj = project3D(p[0], p[1], p[2]);
                path.lineTo(pProj[0], pProj[1]);
            }
            canvas.drawPath(path, executedPathPaint);
        }
    }

    private void draw3dSpindleAndTool(Canvas canvas) {
        // Active Tool Position 3D Projection
        float[] tip = project3D(currentX, currentY, currentZ);
        float[] surface = project3D(currentX, currentY, 0);

        float[] collar = project3D(currentX, currentY, currentZ + 12f);
        float[] spindleTop = project3D(currentX, currentY, currentZ + 35f);

        // 1. Z Depth Indicator Guide Line down to stock bed surface
        canvas.drawLine(tip[0], tip[1], surface[0], surface[1], zDepthLinePaint);

        // Surface footprint ring (Red if Z < 0 cutting depth, Blue if hovering above stock)
        Paint surfaceRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        surfaceRingPaint.setStyle(Paint.Style.STROKE);
        surfaceRingPaint.setStrokeWidth(3.0f);
        surfaceRingPaint.setColor(currentZ < 0 ? Color.parseColor("#EF4444") : Color.parseColor("#3B82F6"));
        canvas.drawCircle(surface[0], surface[1], 16f, surfaceRingPaint);

        // 2. Draw 3D Spindle Housing (Cylinder representation)
        Path spindlePath = new Path();
        float spindleWidth = 22f;
        spindlePath.moveTo(collar[0] - spindleWidth, collar[1]);
        spindlePath.lineTo(collar[0] + spindleWidth, collar[1]);
        spindlePath.lineTo(spindleTop[0] + spindleWidth, spindleTop[1]);
        spindlePath.lineTo(spindleTop[0] - spindleWidth, spindleTop[1]);
        spindlePath.close();

        canvas.drawPath(spindlePath, spindleBodyPaint);

        // Spindle collar band
        canvas.drawRect(collar[0] - spindleWidth - 2, collar[1] - 8,
                collar[0] + spindleWidth + 2, collar[1] + 8, spindleCollarPaint);

        // 3. Draw 3D Tapered Tungsten Tool Bit Cone
        Path toolCone = new Path();
        toolCone.moveTo(collar[0] - 8f, collar[1]);
        toolCone.lineTo(collar[0] + 8f, collar[1]);
        toolCone.lineTo(tip[0], tip[1]); // Cutter tip point
        toolCone.close();

        canvas.drawPath(toolCone, toolBitPaint);
        canvas.drawCircle(tip[0], tip[1], 6f, toolBitPaint);

        // 4. Floating 3D Tool Head Label Badge
        String badgeText = String.format(Locale.US, "X:%.1f Y:%.1f Z:%.1f", currentX, currentY, currentZ);
        float textW = textWhitePaint.measureText(badgeText);

        canvas.drawRect(spindleTop[0] - textW / 2 - 12, spindleTop[1] - 38,
                spindleTop[0] + textW / 2 + 12, spindleTop[1] - 8, hudBgPaint);

        canvas.drawText(badgeText, spindleTop[0] - textW / 2, spindleTop[1] - 16, textWhitePaint);
    }

    private void drawHudOverlay(Canvas canvas, int width, int height) {
        // 1. Perspective Orbit & Reset Hint
        canvas.drawRect(12, 12, 280, 50, hudBgPaint);
        canvas.drawText("3D View (Drag to rotate, pinch zoom)", 20, 36, textMutedPaint);

        // 2. G-Code Motion Legend in Top Right
        canvas.drawRect(width - 220, 12, width - 12, 105, hudBgPaint);

        // Legend G00
        canvas.drawLine(width - 205, 30, width - 175, 30, rapidPaint);
        canvas.drawText("G00 (Rapid)", width - 165, 36, textWhitePaint);

        // Legend G01
        canvas.drawLine(width - 205, 52, width - 175, 52, linearCutPaint);
        canvas.drawText("G01 (Hover)", width - 165, 58, textWhitePaint);

        // Legend G01 Depth Cut
        canvas.drawLine(width - 205, 74, width - 175, 74, depthCutPaint);
        canvas.drawText("G01 (Z<0 Cut)", width - 165, 80, textWhitePaint);

        // Legend Arcs
        canvas.drawLine(width - 205, 94, width - 190, 94, arcCwPaint);
        canvas.drawLine(width - 190, 94, width - 175, 94, arcCcwPaint);
        canvas.drawText("G02/G03 (Arc)", width - 165, 98, textWhitePaint);
    }
}
