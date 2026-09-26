package com.example.brainflowplot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class AccelerometerSampleTest {

    @Test
    public void latestUsesNewestXyzSampleAndTimestamp() {
        double[][] boardData = {
                {0.0, 1.0, 2.0},
                {0.1, 0.2, 0.3},
                {-0.4, -0.5, -0.6},
                {0.7, 0.8, 0.9},
                {0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0},
                {100.0, 101.0, 102.0}
        };

        AccelerometerSample sample =
                AccelerometerSample.latest(boardData, new int[] {1, 2, 3}, 7);

        assertEquals(0.3, sample.xG, 0.0);
        assertEquals(-0.6, sample.yG, 0.0);
        assertEquals(0.9, sample.zG, 0.0);
        assertEquals(102.0, sample.timestamp, 0.0);
        assertEquals(Math.sqrt(1.26), sample.magnitudeG(), 1.0e-12);
    }

    @Test
    public void rejectsMissingAccelerometerAxis() {
        try {
            AccelerometerSample.latest(
                    new double[][] {{1.0}, {2.0}},
                    new int[] {0, 1, 2},
                    -1);
            fail("Expected a missing accelerometer row to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
