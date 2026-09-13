package com.example.brainflowplot;

import org.apache.commons.lang3.tuple.Pair;

import brainflow.DataFilter;
import brainflow.DetrendOperations;
import brainflow.FilterTypes;

/** BrainFlow band-power preprocessing with filter-transient guards on both ends. */
final class BrainFlowBandPowerAnalyzer {

    private static final int FILTER_ORDER = 4;

    private BrainFlowBandPowerAnalyzer() {
    }

    static double[] calculateAverageBandPowers(
            double[][] boardData,
            int[] eegChannels,
            int samplingRate,
            int guardSamples,
            int analysisSamples) throws Exception {
        double[][] cleanAnalysisWindow = EdgeSafeEegWindow.filterAndTrim(
                boardData,
                eegChannels,
                guardSamples,
                analysisSamples,
                signal -> applyStandardFilters(signal, samplingRate));

        Pair<double[], double[]> result = DataFilter.get_avg_band_powers(
                cleanAnalysisWindow,
                EdgeSafeEegWindow.sequentialChannels(cleanAnalysisWindow.length),
                samplingRate,
                false);
        return result.getLeft();
    }

    static void applyStandardFilters(double[] signal, int samplingRate) throws Exception {
        if (signal == null || signal.length == 0) {
            throw new IllegalArgumentException("signal is required");
        }
        if (samplingRate <= 124) {
            throw new IllegalArgumentException(
                    "sampling rate must exceed 124 Hz for the 60 Hz notch filter");
        }

        // Keep this sequence identical to BrainFlow's apply_filters=true band-power path.
        DataFilter.detrend(signal, DetrendOperations.CONSTANT);
        DataFilter.perform_bandstop(
                signal,
                samplingRate,
                48.0,
                52.0,
                FILTER_ORDER,
                FilterTypes.BUTTERWORTH_ZERO_PHASE,
                0.0);
        DataFilter.perform_bandstop(
                signal,
                samplingRate,
                58.0,
                62.0,
                FILTER_ORDER,
                FilterTypes.BUTTERWORTH_ZERO_PHASE,
                0.0);
        DataFilter.perform_bandpass(
                signal,
                samplingRate,
                2.0,
                45.0,
                FILTER_ORDER,
                FilterTypes.BUTTERWORTH_ZERO_PHASE,
                0.0);
    }
}
