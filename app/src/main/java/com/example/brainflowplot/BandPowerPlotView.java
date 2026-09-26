package com.example.brainflowplot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/** A compact five-line powerband history inspired by Muse's Brainwave Powerbands chart. */
public final class BandPowerPlotView extends View {

    private static final int BAND_COUNT = 5;
    private static final int HISTORY_CAPACITY = 60;
    private static final double MIN_POWER = 1.0e-12;
    private static final float MIN_DB_RANGE = 12.0f;
    private static final String[] BAND_NAMES = {"Delta", "Theta", "Alpha", "Beta", "Gamma"};
    private static final int[] BAND_COLORS = {
            0xFFFF6B6B,
            0xFFA78BFA,
            0xFF38BDF8,
            0xFF34D399,
            0xFFFBBF24
    };

    private final float[][] history = new float[BAND_COUNT][HISTORY_CAPACITY];
    private final float[] currentValues = new float[BAND_COUNT];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float density;
    private float scaledDensity;
    private int historySize;
    private int writePosition;

    public BandPowerPlotView(Context context) {
        super(context);
        initialize();
    }

    public BandPowerPlotView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public BandPowerPlotView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public BandPowerPlotView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    private void initialize() {
        density = getResources().getDisplayMetrics().density;
        scaledDensity = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                1.0f,
                getResources().getDisplayMetrics());
        setBackgroundColor(0xFF111827);
        setMinimumHeight(Math.round(280.0f * density));
        reset();
    }

    public void reset() {
        for (float[] bandHistory : history) {
            Arrays.fill(bandHistory, Float.NaN);
        }
        Arrays.fill(currentValues, Float.NaN);
        historySize = 0;
        writePosition = 0;
        setContentDescription(getResources().getString(R.string.band_plot_waiting));
        invalidate();
    }

    public void addBandPowers(double[] bandPowers) {
        if (bandPowers == null || bandPowers.length < BAND_COUNT) {
            throw new IllegalArgumentException("Five BrainFlow band powers are required");
        }
        for (int band = 0; band < BAND_COUNT; band++) {
            double power = bandPowers[band];
            if (!Double.isFinite(power) || power < 0.0) {
                throw new IllegalArgumentException("Band powers must be finite and non-negative");
            }
            float decibels = (float) (10.0 * Math.log10(Math.max(MIN_POWER, power)));
            history[band][writePosition] = decibels;
            currentValues[band] = decibels;
        }
        writePosition = (writePosition + 1) % HISTORY_CAPACITY;
        historySize = Math.min(HISTORY_CAPACITY, historySize + 1);
        setContentDescription(getResources().getString(
                R.string.band_plot_accessibility,
                currentValues[0],
                currentValues[1],
                currentValues[2],
                currentValues[3],
                currentValues[4]));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float legendHeight = 58.0f * density;
        float plotLeft = 46.0f * density;
        float plotTop = legendHeight;
        float plotRight = width - 12.0f * density;
        float plotBottom = height - 24.0f * density;
        if (plotRight <= plotLeft || plotBottom <= plotTop) {
            return;
        }

        drawLegend(canvas, width);
        if (historySize == 0) {
            paint.setColor(0xFFCBD5E1);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(14.0f * scaledDensity);
            canvas.drawText(
                    getResources().getString(R.string.band_plot_waiting),
                    (plotLeft + plotRight) / 2.0f,
                    (plotTop + plotBottom) / 2.0f,
                    paint);
            return;
        }

        float[] range = findDisplayRange();
        float minimum = range[0];
        float maximum = range[1];
        drawGrid(canvas, plotLeft, plotTop, plotRight, plotBottom, minimum, maximum);

        int firstIndex = (writePosition - historySize + HISTORY_CAPACITY) % HISTORY_CAPACITY;
        float xStep = historySize <= 1 ? 0.0f : (plotRight - plotLeft) / (historySize - 1);
        canvas.save();
        canvas.clipRect(plotLeft, plotTop, plotRight, plotBottom);
        for (int band = 0; band < BAND_COUNT; band++) {
            path.reset();
            for (int point = 0; point < historySize; point++) {
                int historyIndex = (firstIndex + point) % HISTORY_CAPACITY;
                float x = historySize == 1 ? plotRight : plotLeft + point * xStep;
                float y = mapY(history[band][historyIndex], plotTop, plotBottom, minimum, maximum);
                if (point == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            paint.setColor(BAND_COLORS[band]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.0f * density);
            canvas.drawPath(path, paint);
            if (historySize == 1) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(plotRight,
                        mapY(currentValues[band], plotTop, plotBottom, minimum, maximum),
                        3.0f * density,
                        paint);
            }
        }
        canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF94A3B8);
        paint.setTextSize(10.0f * scaledDensity);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(
                String.format(Locale.US, "-%d s", Math.max(0, historySize - 1)),
                plotLeft,
                height - 7.0f * density,
                paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(
                getResources().getString(R.string.band_plot_now),
                plotRight,
                height - 7.0f * density,
                paint);
    }

    private void drawLegend(Canvas canvas, float width) {
        float columnWidth = width / BAND_COUNT;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int band = 0; band < BAND_COUNT; band++) {
            float center = columnWidth * band + columnWidth / 2.0f;
            paint.setColor(BAND_COLORS[band]);
            paint.setTextSize(11.0f * scaledDensity);
            canvas.drawText(BAND_NAMES[band], center, 20.0f * density, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(10.0f * scaledDensity);
            String value = Float.isFinite(currentValues[band])
                    ? String.format(Locale.US, "%.1f dB", currentValues[band])
                    : "--";
            canvas.drawText(value, center, 39.0f * density, paint);
        }
    }

    private float[] findDisplayRange() {
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (float[] bandHistory : history) {
            for (float value : bandHistory) {
                if (Float.isFinite(value)) {
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
        }
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum)) {
            return new float[] {-6.0f, 6.0f};
        }
        if (maximum - minimum < MIN_DB_RANGE) {
            float center = (minimum + maximum) / 2.0f;
            minimum = center - MIN_DB_RANGE / 2.0f;
            maximum = center + MIN_DB_RANGE / 2.0f;
        } else {
            float padding = (maximum - minimum) * 0.1f;
            minimum -= padding;
            maximum += padding;
        }
        return new float[] {minimum, maximum};
    }

    private void drawGrid(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            float minimum,
            float maximum) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xFF334155);
        for (int line = 0; line <= 3; line++) {
            float fraction = line / 3.0f;
            float y = top + (bottom - top) * fraction;
            canvas.drawLine(left, y, right, y, paint);

            float value = maximum - (maximum - minimum) * fraction;
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(9.0f * scaledDensity);
            paint.setColor(0xFF94A3B8);
            canvas.drawText(
                    String.format(Locale.US, "%.0f", value),
                    left - 5.0f * density,
                    y + 3.0f * density,
                    paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF334155);
        }
    }

    private float mapY(
            float value, float top, float bottom, float minimum, float maximum) {
        float fraction = (value - minimum) / (maximum - minimum);
        return bottom - fraction * (bottom - top);
    }
}
