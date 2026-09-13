package com.example.brainflowplot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class EdgeSafeEegWindowTest {

    @Test
    public void requiredSamplesIncludesBothGuards() {
        assertEquals(4096, EdgeSafeEegWindow.requiredSamples(2560, 768));
    }

    @Test
    public void filteringUsesNewestPaddedWindowAndDiscardsBothEdges() throws Exception {
        double[][] boardData = {{0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0}};

        double[][] result = EdgeSafeEegWindow.filterAndTrim(
                boardData,
                new int[] {0},
                2,
                3,
                signal -> {
                    assertArrayEquals(
                            new double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0},
                            signal,
                            0.0);
                    signal[0] = -1000.0;
                    signal[1] = -1000.0;
                    signal[5] = 1000.0;
                    signal[6] = 1000.0;
                });

        assertArrayEquals(new double[] {3.0, 4.0, 5.0}, result[0], 0.0);
    }

    @Test
    public void selectedRowsAreCopiedWithoutMutatingBoardData() throws Exception {
        double[][] boardData = {
                {1.0, 2.0, 3.0, 4.0, 5.0},
                {11.0, 12.0, 13.0, 14.0, 15.0}
        };

        double[][] result = EdgeSafeEegWindow.filterAndTrim(
                boardData,
                new int[] {1},
                1,
                3,
                signal -> {
                    for (int i = 0; i < signal.length; i++) {
                        signal[i] += 100.0;
                    }
                });

        assertArrayEquals(new double[] {112.0, 113.0, 114.0}, result[0], 0.0);
        assertArrayEquals(
                new double[] {11.0, 12.0, 13.0, 14.0, 15.0},
                boardData[1],
                0.0);
    }

    @Test
    public void rejectsAWindowWithoutBothGuards() throws Exception {
        try {
            EdgeSafeEegWindow.filterAndTrim(
                    new double[][] {{1.0, 2.0, 3.0, 4.0}},
                    new int[] {0},
                    1,
                    3,
                    signal -> { });
            fail("Expected missing right/left context to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    @Test
    public void sequentialChannelsMatchesExtractedRows() {
        assertArrayEquals(new int[] {0, 1, 2, 3}, EdgeSafeEegWindow.sequentialChannels(4));
    }
}
