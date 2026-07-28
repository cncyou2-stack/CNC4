package com.example.cnc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    private Paint gridPaint;
    private Paint axisXPaint;
    private Paint axisYPaint;

    private Paint rapidPaint;
    private Paint linearCutPaint;
    private Paint arcCwPaint;
    private Paint arcCcwPaint;

    private Paint executedPathPaint;
    private Paint toolPaint;
    private Paint textPaint;
    private Paint legendBgPaint;

    private float currentX = 0f;
    private float currentY = 0f;
    private float currentZ = 0f;

    // Auto-scale & Auto-fit parameters
    private float scale = 4.0f; // px per mm
    private float originX = 120f;
    private float originY = 600f;

    private float minX = 0f, maxX = 50f;
    private float minY = 0f, maxY = 50f;

    private final List<ToolSegment> segments = new ArrayList<>();
    private final Path executedPath = new Path();

    public CncCanvasView(Context context) {
        super(context);
        init();
    }

    public CncCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CncCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#1E293B"));
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));

        axisXPaint = new Paint();
        axisXPaint.setColor(Color.parseColor("#EF4444")); // Red for X Axis
        axisXPaint.setStrokeWidth(3.5f);
        axisXPaint.setAntiAlias(true);

        axisYPaint = new Paint();
        axisYPaint.setColor(Color.parseColor("#22C55E")); // Green for Y Axis
        axisYPaint.setStrokeWidth(3.5f);
        axisYPaint.setAntiAlias(true);

        // G00 Rapid Move (Dashed Light Red / Coral)
        rapidPaint = new Paint();
        rapidPaint.setColor(Color.parseColor("#F87171"));
        rapidPaint.setStrokeWidth(2.5f);
        rapidPaint.setStyle(Paint.Style.STROKE);
        rapidPaint.setPathEffect(new DashPathEffect(new float[]{12, 8}, 0));
        rapidPaint.setAntiAlias(true);

        // G01 Linear Cut (Solid Cyan)
        linearCutPaint = new Paint();
        linearCutPaint.setColor(Color.parseColor("#38BDF8"));
        linearCutPaint.setStrokeWidth(4.5f);
        linearCutPaint.setStyle(Paint.Style.STROKE);
        linearCutPaint.setAntiAlias(true);

        // G02 CW Arc (Solid Amber)
        arcCwPaint = new Paint();
        arcCwPaint.setColor(Color.parseColor("#F59E0B"));
        arcCwPaint.setStrokeWidth(4.5f);
        arcCwPaint.setStyle(Paint.Style.STROKE);
        arcCwPaint.setAntiAlias(true);

        // G03 CCW Arc (Solid Pink/Magenta)
        arcCcwPaint = new Paint();
        arcCcwPaint.setColor(Color.parseColor("#EC4899"));
        arcCcwPaint.setStrokeWidth(4.5f);
        arcCcwPaint.setStyle(Paint.Style.STROKE);
        arcCcwPaint.setAntiAlias(true);

        // Executed trace
        executedPathPaint = new Paint();
        executedPathPaint.setColor(Color.parseColor("#A855F7")); // Purple trace
        executedPathPaint.setStrokeWidth(5.5f);
        executedPathPaint.setStyle(Paint.Style.STROKE);
        executedPathPaint.setAntiAlias(true);

        // Tool bit head
        toolPaint = new Paint();
        toolPaint.setColor(Color.parseColor("#FBBF24"));
        toolPaint.setAntiAlias(true);
        toolPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(22f);
        textPaint.setAntiAlias(true);

        legendBgPaint = new Paint();
        legendBgPaint.setColor(Color.parseColor("#1E293B"));
        legendBgPaint.setStyle(Paint.Style.FILL);
        legendBgPaint.setAlpha(220);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculateScaleAndBounds();
    }

    public void setToolPosition(float x, float y, float z) {
        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;

        float canvasPx = toCanvasX(x);
        float canvasPy = toCanvasY(y);

        if (executedPath.isEmpty()) {
            executedPath.moveTo(canvasPx, canvasPy);
        } else {
            executedPath.lineTo(canvasPx, canvasPy);
        }

        invalidate();
    }

    public void addSegment(ToolSegment segment) {
        segments.add(segment);
        invalidate();
    }

    public void clearAll() {
        segments.clear();
        executedPath.reset();
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
        } else {
            minX = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;

            // Always include origin (0,0) for baseline axes
            minX = Math.min(minX, 0f);
            maxX = Math.max(maxX, 0f);
            minY = Math.min(minY, 0f);
            maxY = Math.max(maxY, 0f);

            for (ToolSegment seg : segments) {
                minX = Math.min(minX, Math.min(seg.startX, seg.endX));
                maxX = Math.max(maxX, Math.max(seg.startX, seg.endX));
                minY = Math.min(minY, Math.min(seg.startY, seg.endY));
                maxY = Math.max(maxY, Math.max(seg.startY, seg.endY));

                if (seg.type == MotionType.ARC_CW_G02 || seg.type == MotionType.ARC_CCW_G03) {
                    double cx = seg.startX + seg.iOffset;
                    double cy = seg.startY + seg.jOffset;
                    double r = Math.hypot(seg.iOffset, seg.jOffset);
                    if (r > 0) {
                        minX = Math.min(minX, (float) (cx - r));
                        maxX = Math.max(maxX, (float) (cx + r));
                        minY = Math.min(minY, (float) (cy - r));
                        maxY = Math.max(maxY, (float) (cy + r));
                    }
                }
            }
        }

        float viewW = getWidth();
        float viewH = getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float contentW = Math.max(15f, maxX - minX);
        float contentH = Math.max(15f, maxY - minY);

        float paddingPx = 55f;
        float availW = Math.max(100f, viewW - 2 * paddingPx);
        float availH = Math.max(100f, viewH - 2 * paddingPx);

        scale = Math.min(availW / contentW, availH / contentH);
        scale = Math.max(0.1f, Math.min(scale, 25.0f));

        float midX = (minX + maxX) / 2f;
        float midY = (minY + maxY) / 2f;

        originX = (viewW / 2f) - (midX * scale);
        originY = (viewH / 2f) + (midY * scale);

        invalidate();
    }

    private float toCanvasX(float mmX) {
        return originX + (mmX * scale);
    }

    private float toCanvasY(float mmY) {
        return originY - (mmY * scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 1. Draw Grid
        float gridIntervalMm = scale > 12f ? 5f : (scale > 4f ? 10f : (scale > 1.5f ? 20f : 50f));
        float gridIntervalPx = gridIntervalMm * scale;

        if (gridIntervalPx > 10) {
            for (float x = originX; x < width; x += gridIntervalPx) {
                canvas.drawLine(x, 0, x, height, gridPaint);
            }
            for (float x = originX; x > 0; x -= gridIntervalPx) {
                canvas.drawLine(x, 0, x, height, gridPaint);
            }
            for (float y = originY; y < height; y += gridIntervalPx) {
                canvas.drawLine(0, y, width, y, gridPaint);
            }
            for (float y = originY; y > 0; y -= gridIntervalPx) {
                canvas.drawLine(0, y, width, y, gridPaint);
            }
        }

        // 2. Draw Main Origin Axes
        canvas.drawLine(0, originY, width, originY, axisXPaint);
        canvas.drawLine(originX, 0, originX, height, axisYPaint);

        canvas.drawText("X+", Math.min(width - 40, originX + 150), originY - 10, axisXPaint);
        canvas.drawText("Y+", originX + 10, Math.max(30, originY - 150), axisYPaint);
        canvas.drawText("(0,0)", originX + 8, originY + 25, textPaint);

        // 3. Draw Planned G-Code Segments
        for (ToolSegment seg : segments) {
            float x1 = toCanvasX(seg.startX);
            float y1 = toCanvasY(seg.startY);
            float x2 = toCanvasX(seg.endX);
            float y2 = toCanvasY(seg.endY);

            if (seg.type == MotionType.RAPID_G00) {
                // Dashed line for rapid positioning
                canvas.drawLine(x1, y1, x2, y2, rapidPaint);
            } else if (seg.type == MotionType.LINEAR_G01) {
                // Solid cyan line for linear cut
                canvas.drawLine(x1, y1, x2, y2, linearCutPaint);
            } else if (seg.type == MotionType.ARC_CW_G02 || seg.type == MotionType.ARC_CCW_G03) {
                // Arc Interpolation based on center offsets (I, J)
                double cx = seg.startX + seg.iOffset;
                double cy = seg.startY + seg.jOffset;
                double radius = Math.hypot(seg.iOffset, seg.jOffset);

                if (radius < 1e-3) {
                    canvas.drawLine(x1, y1, x2, y2, (seg.type == MotionType.ARC_CW_G02) ? arcCwPaint : arcCcwPaint);
                } else {
                    double startAngle = Math.atan2(seg.startY - cy, seg.startX - cx);
                    double endAngle = Math.atan2(seg.endY - cy, seg.endX - cx);

                    double sweep = endAngle - startAngle;

                    if (seg.type == MotionType.ARC_CW_G02) {
                        if (sweep >= 0) sweep -= 2 * Math.PI;
                    } else { // CCW
                        if (sweep <= 0) sweep += 2 * Math.PI;
                    }

                    Paint arcPaint = (seg.type == MotionType.ARC_CW_G02) ? arcCwPaint : arcCcwPaint;
                    int steps = Math.max(24, (int) (Math.abs(sweep) * 24));
                    float prevPx = x1;
                    float prevPy = y1;

                    for (int step = 1; step <= steps; step++) {
                        double angle = startAngle + (sweep * step / steps);
                        float curMmX = (float) (cx + radius * Math.cos(angle));
                        float curMmY = (float) (cy + radius * Math.sin(angle));

                        float curPx = toCanvasX(curMmX);
                        float curPy = toCanvasY(curMmY);

                        canvas.drawLine(prevPx, prevPy, curPx, curPy, arcPaint);
                        prevPx = curPx;
                        prevPy = curPy;
                    }
                }
            }
        }

        // 4. Draw Executed Trajectory
        canvas.drawPath(executedPath, executedPathPaint);

        // 5. Draw Tool Head Cursor
        float toolPx = toCanvasX(currentX);
        float toolPy = toCanvasY(currentY);

        canvas.drawCircle(toolPx, toolPy, 12f, toolPaint);

        // Z-depth ring indicator (Red when Z < 0 cutting, Blue when Z >= 0 rapid hover)
        Paint zRingPaint = new Paint();
        zRingPaint.setStyle(Paint.Style.STROKE);
        zRingPaint.setStrokeWidth(3f);
        zRingPaint.setAntiAlias(true);
        if (currentZ < 0) {
            zRingPaint.setColor(Color.parseColor("#EF4444")); // Red ring during cutting depth
        } else {
            zRingPaint.setColor(Color.parseColor("#3B82F6")); // Blue ring above stock
        }
        canvas.drawCircle(toolPx, toolPy, 20f, zRingPaint);

        // 6. Draw Color Legend in Top Right
        canvas.drawRect(width - 210, 10, width - 10, 95, legendBgPaint);

        Paint legTextPaint = new Paint();
        legTextPaint.setColor(Color.parseColor("#F8FAFC"));
        legTextPaint.setTextSize(18f);
        legTextPaint.setAntiAlias(true);

        // G00
        canvas.drawLine(width - 200, 28, width - 170, 28, rapidPaint);
        canvas.drawText("G00 (Rapid)", width - 160, 33, legTextPaint);

        // G01
        canvas.drawLine(width - 200, 48, width - 170, 48, linearCutPaint);
        canvas.drawText("G01 (Linear)", width - 160, 53, legTextPaint);

        // G02 / G03
        canvas.drawLine(width - 200, 68, width - 185, 68, arcCwPaint);
        canvas.drawLine(width - 185, 68, width - 170, 68, arcCcwPaint);
        canvas.drawText("G02/G03 (Arc)", width - 160, 73, legTextPaint);
    }
}
