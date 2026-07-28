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

    private float currentX = 0f;
    private float currentY = 0f;
    private float currentZ = 0f;

    private float scale = 4.0f; // 4 pixels per mm
    private float originX = 120f;
    private float originY = 600f;

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
        axisXPaint.setStrokeWidth(4f);
        axisXPaint.setAntiAlias(true);

        axisYPaint = new Paint();
        axisYPaint.setColor(Color.parseColor("#22C55E")); // Green for Y Axis
        axisYPaint.setStrokeWidth(4f);
        axisYPaint.setAntiAlias(true);

        // G00 Rapid Move (Dashed Red/Orange)
        rapidPaint = new Paint();
        rapidPaint.setColor(Color.parseColor("#F87171"));
        rapidPaint.setStrokeWidth(2.5f);
        rapidPaint.setStyle(Paint.Style.STROKE);
        rapidPaint.setPathEffect(new DashPathEffect(new float[]{10, 8}, 0));
        rapidPaint.setAntiAlias(true);

        // G01 Linear Cut (Solid Cyan)
        linearCutPaint = new Paint();
        linearCutPaint.setColor(Color.parseColor("#38BDF8"));
        linearCutPaint.setStrokeWidth(4f);
        linearCutPaint.setStyle(Paint.Style.STROKE);
        linearCutPaint.setAntiAlias(true);

        // G02 CW Arc (Solid Amber)
        arcCwPaint = new Paint();
        arcCwPaint.setColor(Color.parseColor("#F59E0B"));
        arcCwPaint.setStrokeWidth(4f);
        arcCwPaint.setStyle(Paint.Style.STROKE);
        arcCwPaint.setAntiAlias(true);

        // G03 CCW Arc (Solid Magenta)
        arcCcwPaint = new Paint();
        arcCcwPaint.setColor(Color.parseColor("#EC4899"));
        arcCcwPaint.setStrokeWidth(4f);
        arcCcwPaint.setStyle(Paint.Style.STROKE);
        arcCcwPaint.setAntiAlias(true);

        // Executed trace
        executedPathPaint = new Paint();
        executedPathPaint.setColor(Color.parseColor("#A855F7"));
        executedPathPaint.setStrokeWidth(5f);
        executedPathPaint.setStyle(Paint.Style.STROKE);
        executedPathPaint.setAntiAlias(true);

        // Tool bit head
        toolPaint = new Paint();
        toolPaint.setColor(Color.parseColor("#FBBF24"));
        toolPaint.setAntiAlias(true);
        toolPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(26f);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        originX = w * 0.15f;
        originY = h * 0.80f;
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
        float gridInterval = 10 * scale; // 10mm grid
        for (float x = originX; x < width; x += gridInterval) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float x = originX; x > 0; x -= gridInterval) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float y = originY; y < height; y += gridInterval) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }
        for (float y = originY; y > 0; y -= gridInterval) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // 2. Draw Axes
        canvas.drawLine(originX, originY, originX + 220, originY, axisXPaint);
        canvas.drawText("X+", originX + 230, originY + 8, axisXPaint);

        canvas.drawLine(originX, originY, originX, originY - 220, axisYPaint);
        canvas.drawText("Y+", originX - 8, originY - 230, axisYPaint);

        canvas.drawText("(0,0)", originX - 50, originY + 35, textPaint);

        // 3. Draw Planned Segments (G00, G01, G02, G03)
        for (ToolSegment seg : segments) {
            float x1 = toCanvasX(seg.startX);
            float y1 = toCanvasY(seg.startY);
            float x2 = toCanvasX(seg.endX);
            float y2 = toCanvasY(seg.endY);

            if (seg.type == MotionType.RAPID_G00) {
                canvas.drawLine(x1, y1, x2, y2, rapidPaint);
            } else if (seg.type == MotionType.LINEAR_G01) {
                canvas.drawLine(x1, y1, x2, y2, linearCutPaint);
            } else if (seg.type == MotionType.ARC_CW_G02 || seg.type == MotionType.ARC_CCW_G03) {
                // Arc Interpolation
                float centerX = seg.startX + seg.iOffset;
                float centerY = seg.startY + seg.jOffset;

                double startAngle = Math.atan2(seg.startY - centerY, seg.startX - centerX);
                double endAngle = Math.atan2(seg.endY - centerY, seg.endX - centerX);

                double sweep = endAngle - startAngle;

                if (seg.type == MotionType.ARC_CW_G02) {
                    if (sweep >= 0) sweep -= 2 * Math.PI;
                } else { // CCW
                    if (sweep <= 0) sweep += 2 * Math.PI;
                }

                Paint arcPaint = (seg.type == MotionType.ARC_CW_G02) ? arcCwPaint : arcCcwPaint;
                int steps = 30;
                float prevX = x1;
                float prevY = y1;

                for (int i = 1; i <= steps; i++) {
                    double angle = startAngle + (sweep * i / steps);
                    float curMmX = (float) (centerX + Math.cos(angle) * Math.hypot(seg.iOffset, seg.jOffset));
                    float curMmY = (float) (centerY + Math.sin(angle) * Math.hypot(seg.iOffset, seg.jOffset));

                    float curPx = toCanvasX(curMmX);
                    float curPy = toCanvasY(curMmY);

                    canvas.drawLine(prevX, prevY, curPx, curPy, arcPaint);
                    prevX = curPx;
                    prevY = curPy;
                }
            }
        }

        // 4. Draw Executed Trajectory
        canvas.drawPath(executedPath, executedPathPaint);

        // 5. Draw Tool Cursor Head
        float toolPx = toCanvasX(currentX);
        float toolPy = toCanvasY(currentY);

        canvas.drawCircle(toolPx, toolPy, 14f, toolPaint);

        // Z-depth ring indicator
        Paint zRingPaint = new Paint();
        zRingPaint.setStyle(Paint.Style.STROKE);
        zRingPaint.setStrokeWidth(3f);
        zRingPaint.setAntiAlias(true);
        if (currentZ < 0) {
            zRingPaint.setColor(Color.parseColor("#EF4444")); // Red ring when cutting into material
        } else {
            zRingPaint.setColor(Color.parseColor("#3B82F6")); // Blue ring above material
        }
        canvas.drawCircle(toolPx, toolPy, 22f, zRingPaint);
    }
}
