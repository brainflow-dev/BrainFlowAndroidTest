package com.example.brainflowplot;

/** Fixed-size chronological buffer for BrainFlow's row-major board data. */
final class RollingBoardBuffer {

    private final double[][] values;
    private final int capacity;
    private int size;
    private int writePosition;

    RollingBoardBuffer(int rows, int capacity) {
        if (rows <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("rows and capacity must be positive");
        }
        this.values = new double[rows][capacity];
        this.capacity = capacity;
    }

    void append(double[][] batch) {
        int sampleCount = validateAndGetSampleCount(batch);
        if (sampleCount == 0) {
            return;
        }

        int samplesToCopy = Math.min(sampleCount, capacity);
        int sourceStart = sampleCount - samplesToCopy;
        if (samplesToCopy == capacity) {
            for (int row = 0; row < values.length; row++) {
                System.arraycopy(batch[row], sourceStart, values[row], 0, capacity);
            }
            size = capacity;
            writePosition = 0;
            return;
        }

        int firstPart = Math.min(samplesToCopy, capacity - writePosition);
        int secondPart = samplesToCopy - firstPart;
        for (int row = 0; row < values.length; row++) {
            System.arraycopy(batch[row], sourceStart, values[row], writePosition, firstPart);
            if (secondPart > 0) {
                System.arraycopy(
                        batch[row], sourceStart + firstPart, values[row], 0, secondPart);
            }
        }
        writePosition = (writePosition + samplesToCopy) % capacity;
        size = Math.min(capacity, size + samplesToCopy);
    }

    int size() {
        return size;
    }

    int capacity() {
        return capacity;
    }

    double[][] snapshot() {
        double[][] result = new double[values.length][size];
        if (size == 0) {
            return result;
        }

        int start = (writePosition - size + capacity) % capacity;
        int firstPart = Math.min(size, capacity - start);
        int secondPart = size - firstPart;
        for (int row = 0; row < values.length; row++) {
            System.arraycopy(values[row], start, result[row], 0, firstPart);
            if (secondPart > 0) {
                System.arraycopy(values[row], 0, result[row], firstPart, secondPart);
            }
        }
        return result;
    }

    private int validateAndGetSampleCount(double[][] batch) {
        if (batch == null || batch.length != values.length) {
            throw new IllegalArgumentException("BrainFlow row count changed");
        }
        if (batch.length == 0 || batch[0] == null) {
            return 0;
        }
        int sampleCount = batch[0].length;
        for (double[] row : batch) {
            if (row == null || row.length != sampleCount) {
                throw new IllegalArgumentException("BrainFlow rows have different lengths");
            }
        }
        return sampleCount;
    }
}
