package com.example.cnc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CncCanvasView extends View {

    private Paint gridPaint;
    private Paint axisXPaint;
    private Paint axisYPaint;
    private Paint gcodePathPaint;
    private Paint currentPathPaint;
    private Paint toolPaint;
    private Paint textPaint;

    private float currentX = 0f;
    private float currentY = 0f;
    private float currentZ = 0f;

    // Scale factor: pixels per mm
    private float scale = 3.0f;
    // Origin offset in canvas pixels
    private float originX = 100f;
    private float originY = 500f;

    private final List<PointF> gcodePoints = new ArrayList<>();
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
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

        axisXPaint = new Paint();
        axisXPaint.setColor(Color.parseColor("#EF4444")); // Red for X
        axisXPaint.setStrokeWidth(3f);
        axisXPaint.setAntiAlias(true);

        axisYPaint = new Paint();
        axisYPaint.setColor(Color.parseColor("#22C55E")); // Green for Y
        axisYPaint.setStrokeWidth(3f);
        axisYPaint.setAntiAlias(true);

        gcodePathPaint = new Paint();
        gcodePathPaint.setColor(Color.parseColor("#38BDF8")); // Cyan for Toolpath
        gcodePathPaint.setStrokeWidth(3f);
        gcodePathPaint.setStyle(Paint.Style.STROKE);
        gcodePathPaint.setAntiAlias(true);

        currentPathPaint = new Paint();
        currentPathPaint.setColor(Color.parseColor("#F59E0B")); // Amber for executed path
        currentPathPaint.setStrokeWidth(4f);
        currentPathPaint.setStyle(Paint.Style.STROKE);
        currentPathPaint.setAntiAlias(true);

        toolPaint = new Paint();
        toolPaint.setColor(Color.parseColor("#E11D48")); // Rose for Tool Bit
        toolPaint.setAntiAlias(true);
        toolPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#64748B"));
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        // Add a default sample CNC square path
        addPoint(0, 0);
        addPoint(50, 0);
        addPoint(50, 50);
        addPoint(0, 50);
        addPoint(0, 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        originX = w * 0.15f;
        originY = h * 0.85f;
    }

    public void setToolPosition(float x, float y, float z) {
        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;

        float canvasPx = originX + (x * scale);
        float canvasPy = originY - (y * scale);

        if (executedPath.isEmpty()) {
            executedPath.moveTo(canvasPx, canvasPy);
        } else {
            executedPath.lineTo(canvasPx, canvasPy);
        }

        invalidate();
    }

    public void addPoint(float x, float y) {
        gcodePoints.add(new PointF(x, y));
        invalidate();
    }

    public void clearPaths() {
        gcodePoints.clear();
        executedPath.reset();
        currentX = 0;
        currentY = 0;
        currentZ = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // 1. Draw Grid
        float gridSizePx = 10 * scale; // 10mm grid
        for (float x = originX; x < width; x += gridSizePx) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float x = originX; x > 0; x -= gridSizePx) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float y = originY; y < height; y += gridSizePx) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }
        for (float y = originY; y > 0; y -= gridSizePx) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // 2. Draw Axes
        canvas.drawLine(originX, originY, originX + 200, originY, axisXPaint); // X Axis
        canvas.drawText("X", originX + 210, originY + 8, axisXPaint);

        canvas.drawLine(originX, originY, originX, originY - 200, axisYPaint); // Y Axis
        canvas.drawText("Y", originX - 8, originY - 210, axisYPaint);

        canvas.drawText("(0,0)", originX - 40, originY + 30, textPaint);

        // 3. Draw Planned G-Code Toolpath
        if (gcodePoints.size() > 1) {
            Path planPath = new Path();
            PointF first = gcodePoints.get(0);
            planPath.moveTo(originX + (first.x * scale), originY - (first.y * scale));

            for (int i = 1; i < gcodePoints.size(); i++) {
                PointF p = gcodePoints.get(i);
                planPath.lineTo(originX + (p.x * scale), originY - (p.y * scale));
            }
            canvas.drawPath(planPath, gcodePathPaint);
        }

        // 4. Draw Executed Trajectory
        canvas.drawPath(executedPath, currentPathPaint);

        // 5. Draw Tool Cursor
        float toolPx = originX + (currentX * scale);
        float toolPy = originY - (currentY * scale);

        // Draw Tool Circle
        canvas.drawCircle(toolPx, toolPy, 12f, toolPaint);

        // Draw Z depth indicator ring
        Paint ringPaint = new Paint();
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);
        ringPaint.setColor(currentZ < 0 ? Color.RED : Color.CYAN);
        canvas.drawCircle(toolPx, toolPy, 20f, ringPaint);
    }
}
