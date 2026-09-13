package com.example.brainflowplot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import brainflow.BrainFlowClassifiers;
import brainflow.BrainFlowMetrics;
import brainflow.BrainFlowModelParams;
import brainflow.MLModel;

@RunWith(AndroidJUnit4.class)
public class BrainFlowAnalysisInstrumentedTest {

    @Test
    public void nativeClassifierMovingAverageMatchesRawPredictionMean() throws Exception {
        double[][] featureVectors = {
                {0.10, 0.15, 0.45, 0.20, 0.10},
                {0.35, 0.25, 0.10, 0.20, 0.10},
                {0.05, 0.10, 0.15, 0.30, 0.40},
                {0.20, 0.40, 0.25, 0.10, 0.05},
                {0.40, 0.10, 0.20, 0.20, 0.10}
        };
        int defaultWindow = 5;

        double[] raw = predictSequence(featureVectors, "");
        double[] smoothed = predictSequence(featureVectors, "{\"moving_average\":true}");

        for (int i = 0; i < smoothed.length; i++) {
            int first = Math.max(0, i - defaultWindow + 1);
            double expected = 0.0;
            for (int j = first; j <= i; j++) {
                expected += raw[j];
            }
            expected /= i - first + 1;
            assertEquals(expected, smoothed[i], 1.0e-9);
        }
    }

    @Test
    public void realContextRemovesFilterTransientsFromBothAnalysisEdges() throws Exception {
        int samplingRate = 256;
        int analysisSamples = 10 * samplingRate;
        int guardSamples = 3 * samplingRate;
        int centerStart = 12 * samplingRate;
        double[] raw = syntheticEeg(29 * samplingRate, samplingRate);

        double[] longContextReference = Arrays.copyOf(raw, raw.length);
        BrainFlowBandPowerAnalyzer.applyStandardFilters(longContextReference, samplingRate);
        double[] referenceCenter = Arrays.copyOfRange(
                longContextReference, centerStart, centerStart + analysisSamples);

        double[] guardedSource = Arrays.copyOfRange(
                raw,
                centerStart - guardSamples,
                centerStart + analysisSamples + guardSamples);
        double[] guardedCenter = EdgeSafeEegWindow.filterAndTrim(
                new double[][] {guardedSource},
                new int[] {0},
                guardSamples,
                analysisSamples,
                signal -> BrainFlowBandPowerAnalyzer.applyStandardFilters(
                        signal, samplingRate))[0];

        double[] unguardedCenter = Arrays.copyOfRange(
                raw, centerStart, centerStart + analysisSamples);
        BrainFlowBandPowerAnalyzer.applyStandardFilters(unguardedCenter, samplingRate);

        int edgeSamples = samplingRate;
        double guardedLeftError = rmsError(
                guardedCenter, referenceCenter, 0, edgeSamples);
        double directLeftError = rmsError(
                unguardedCenter, referenceCenter, 0, edgeSamples);
        double guardedRightError = rmsError(
                guardedCenter,
                referenceCenter,
                analysisSamples - edgeSamples,
                analysisSamples);
        double directRightError = rmsError(
                unguardedCenter,
                referenceCenter,
                analysisSamples - edgeSamples,
                analysisSamples);

        assertTrue(
                "left guard should suppress the start transient: guarded="
                        + guardedLeftError + ", direct=" + directLeftError,
                guardedLeftError < directLeftError * 0.20);
        assertTrue(
                "right guard should suppress the end transient: guarded="
                        + guardedRightError + ", direct=" + directRightError,
                guardedRightError < directRightError * 0.20);

        double[] powers = BrainFlowBandPowerAnalyzer.calculateAverageBandPowers(
                new double[][] {guardedSource},
                new int[] {0},
                samplingRate,
                guardSamples,
                analysisSamples);
        assertEquals(5, powers.length);
        double sum = 0.0;
        for (double power : powers) {
            assertTrue(Double.isFinite(power));
            assertTrue(power >= 0.0);
            sum += power;
        }
        assertEquals(1.0, sum, 1.0e-9);
    }

    private static double[] predictSequence(double[][] featureVectors, String otherInfo)
            throws Exception {
        BrainFlowModelParams params = new BrainFlowModelParams(
                BrainFlowMetrics.RESTFULNESS,
                BrainFlowClassifiers.DEFAULT_CLASSIFIER);
        params.set_other_info(otherInfo);
        MLModel model = new MLModel(params);
        boolean prepared = false;
        try {
            model.prepare();
            prepared = true;
            double[] result = new double[featureVectors.length];
            for (int i = 0; i < featureVectors.length; i++) {
                result[i] = model.predict(featureVectors[i])[0];
            }
            return result;
        } finally {
            if (prepared) {
                model.release();
            }
        }
    }

    private static double[] syntheticEeg(int samples, int samplingRate) {
        double[] signal = new double[samples];
        for (int i = 0; i < samples; i++) {
            double time = i / (double) samplingRate;
            signal[i] = 70.0 * Math.sin(2.0 * Math.PI * 10.0 * time + 0.37)
                    + 35.0 * Math.sin(2.0 * Math.PI * 0.7 * time + 1.11)
                    + 20.0 * Math.sin(2.0 * Math.PI * 50.0 * time + 0.23)
                    + 15.0 * Math.sin(2.0 * Math.PI * 60.0 * time + 0.89)
                    + 0.015 * i;
        }
        return signal;
    }

    private static double rmsError(
            double[] actual, double[] expected, int startInclusive, int endExclusive) {
        double squaredError = 0.0;
        for (int i = startInclusive; i < endExclusive; i++) {
            double error = actual[i] - expected[i];
            squaredError += error * error;
        }
        return Math.sqrt(squaredError / (endExclusive - startInclusive));
    }
}
