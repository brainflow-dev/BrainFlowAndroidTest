package com.example.brainflowplot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RollingBoardBufferTest {

    @Test
    public void snapshotPreservesChronologicalOrder() {
        RollingBoardBuffer buffer = new RollingBoardBuffer(2, 5);

        buffer.append(new double[][] {{1.0, 2.0}, {11.0, 12.0}});
        buffer.append(new double[][] {{3.0, 4.0}, {13.0, 14.0}});

        assertEquals(4, buffer.size());
        assertArrayEquals(new double[] {1.0, 2.0, 3.0, 4.0}, buffer.snapshot()[0], 0.0);
        assertArrayEquals(new double[] {11.0, 12.0, 13.0, 14.0}, buffer.snapshot()[1], 0.0);
    }

    @Test
    public void snapshotKeepsNewestSamplesAfterWrap() {
        RollingBoardBuffer buffer = new RollingBoardBuffer(1, 4);

        buffer.append(new double[][] {{1.0, 2.0, 3.0}});
        buffer.append(new double[][] {{4.0, 5.0, 6.0}});

        assertEquals(4, buffer.size());
        assertEquals(4, buffer.capacity());
        assertArrayEquals(new double[] {3.0, 4.0, 5.0, 6.0}, buffer.snapshot()[0], 0.0);
    }

    @Test
    public void oversizedBatchKeepsOnlyItsNewestSamples() {
        RollingBoardBuffer buffer = new RollingBoardBuffer(1, 3);

        buffer.append(new double[][] {{1.0, 2.0, 3.0, 4.0, 5.0}});

        assertEquals(3, buffer.size());
        assertArrayEquals(new double[] {3.0, 4.0, 5.0}, buffer.snapshot()[0], 0.0);
    }
}
