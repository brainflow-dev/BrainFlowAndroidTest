package com.example.brainflowplot;

/** Latest three-axis Muse acceleration sample from BrainFlow's auxiliary preset. */
final class AccelerometerSample {

    final double xG;
    final double yG;
    final double zG;
    final double timestamp;

    private AccelerometerSample(double xG, double yG, double zG, double timestamp) {
        this.xG = xG;
        this.yG = yG;
        this.zG = zG;
        this.timestamp = timestamp;
    }

    static AccelerometerSample latest(
            double[][] boardData, int[] accelChannels, int timestampChannel) {
        if (boardData == null || accelChannels == null || accelChannels.length < 3) {
            throw new IllegalArgumentException(
                    "board data and three accelerometer channels are required");
        }

        double[] xRow = row(boardData, accelChannels[0], "X");
        double[] yRow = row(boardData, accelChannels[1], "Y");
        double[] zRow = row(boardData, accelChannels[2], "Z");
        int sampleCount = Math.min(xRow.length, Math.min(yRow.length, zRow.length));
        if (sampleCount == 0) {
            throw new IllegalArgumentException("accelerometer data contains no samples");
        }

        int column = sampleCount - 1;
        double xG = xRow[column];
        double yG = yRow[column];
        double zG = zRow[column];
        if (!Double.isFinite(xG) || !Double.isFinite(yG) || !Double.isFinite(zG)) {
            throw new IllegalArgumentException("accelerometer sample is not finite");
        }

        double timestamp = Double.NaN;
        if (timestampChannel >= 0 && timestampChannel < boardData.length) {
            double[] timestampRow = boardData[timestampChannel];
            if (timestampRow != null && timestampRow.length >= sampleCount) {
                timestamp = timestampRow[column];
            }
        }
        return new AccelerometerSample(xG, yG, zG, timestamp);
    }

    double magnitudeG() {
        return Math.sqrt(xG * xG + yG * yG + zG * zG);
    }

    private static double[] row(double[][] boardData, int channel, String axis) {
        if (channel < 0 || channel >= boardData.length || boardData[channel] == null) {
            throw new IllegalArgumentException(axis + " accelerometer channel is unavailable");
        }
        return boardData[channel];
    }
}
