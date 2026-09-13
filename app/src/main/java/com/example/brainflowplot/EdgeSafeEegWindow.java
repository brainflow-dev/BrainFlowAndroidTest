package com.example.brainflowplot;

import java.util.Arrays;

/** Builds an analysis window only after filtering it with real context on both sides. */
final class EdgeSafeEegWindow {

    @FunctionalInterface
    interface RowFilter {
        void apply(double[] signal) throws Exception;
    }

    private EdgeSafeEegWindow() {
    }

    static int requiredSamples(int analysisSamples, int guardSamples) {
        if (analysisSamples <= 0 || guardSamples <= 0) {
            throw new IllegalArgumentException("analysis and guard sample counts must be positive");
        }
        try {
            return Math.addExact(analysisSamples, Math.multiplyExact(guardSamples, 2));
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("analysis window is too large", error);
        }
    }

    static double[][] filterAndTrim(
            double[][] boardData,
            int[] eegChannels,
            int guardSamples,
            int analysisSamples,
            RowFilter filter) throws Exception {
        if (boardData == null || eegChannels == null || eegChannels.length == 0) {
            throw new IllegalArgumentException("board data and EEG channels are required");
        }
        if (filter == null) {
            throw new IllegalArgumentException("row filter is required");
        }

        int requiredSamples = requiredSamples(analysisSamples, guardSamples);
        double[][] result = new double[eegChannels.length][analysisSamples];
        for (int outputRow = 0; outputRow < eegChannels.length; outputRow++) {
            int sourceRow = eegChannels[outputRow];
            if (sourceRow < 0 || sourceRow >= boardData.length) {
                throw new IllegalArgumentException("EEG channel is outside the board data");
            }
            double[] source = boardData[sourceRow];
            if (source == null || source.length < requiredSamples) {
                throw new IllegalArgumentException(
                        "EEG channel does not contain both filter guards and the analysis window");
            }

            int sourceStart = source.length - requiredSamples;
            double[] paddedSignal = Arrays.copyOfRange(
                    source, sourceStart, sourceStart + requiredSamples);
            filter.apply(paddedSignal);
            System.arraycopy(
                    paddedSignal,
                    guardSamples,
                    result[outputRow],
                    0,
                    analysisSamples);
        }
        return result;
    }

    static int[] sequentialChannels(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("channel count must be positive");
        }
        int[] channels = new int[count];
        for (int i = 0; i < count; i++) {
            channels[i] = i;
        }
        return channels;
    }
}
