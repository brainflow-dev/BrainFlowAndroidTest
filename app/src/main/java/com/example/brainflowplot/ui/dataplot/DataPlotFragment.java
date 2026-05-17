package com.example.brainflowplot.ui.dataplot;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.brainflowplot.DataActivity;
import com.example.brainflowplot.R;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import brainflow.AggOperations;
import brainflow.DataFilter;
import brainflow.FilterTypes;

public class DataPlotFragment extends Fragment {

    private static final String TAG = "BrainFlowLog";
    private int windowSize = 8;
    private int timeSleep = 50; // 20fps
    private GraphView graph = null;
    private LineGraphSeries<DataPoint>[] series= null;
    private Runnable worker = null;
    private final Handler handler = new Handler();
    private static int downnsamplingOrder = 1;
    private static final int maxValue = 1000;
    private int emptyDataLogCounter = 0;
    private boolean firstSamplesLogged = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_dataplot, container, false);

        graph = (GraphView) rootView.findViewById(R.id.datagraph);
        graph.getViewport().setScalableY(true);
        graph.setTitle("TimeSeries");
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!DataActivity.isSessionReady || DataActivity.boardShim == null || DataActivity.channels == null || DataActivity.channels.length == 0 || DataActivity.samplingRate <= 0) {
            Log.i(TAG, "TimeSeries plot is waiting for session: isSessionReady="
                    + DataActivity.isSessionReady
                    + ", boardShimSet=" + (DataActivity.boardShim != null)
                    + ", channelCount=" + (DataActivity.channels == null ? 0 : DataActivity.channels.length)
                    + ", samplingRate=" + DataActivity.samplingRate);
            return;
        }

        // don't plot too many datapoints
        downnsamplingOrder = 1;
        int desiredSamplingRate = 500;
        if (DataActivity.samplingRate > desiredSamplingRate) {
            downnsamplingOrder = DataActivity.samplingRate / desiredSamplingRate;
        }

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(windowSize * DataActivity.samplingRate / downnsamplingOrder);
        series = new LineGraphSeries[DataActivity.channels.length];

        for (int i = 0; i < DataActivity.channels.length; i++) {
            series[i] = new LineGraphSeries<DataPoint>();
            series[i].setColor(DataActivity.colors[i % DataActivity.colors.length]);
            graph.addSeries(series[i]);
        }
        Log.i(TAG, "TimeSeries plot started: samplingRate=" + DataActivity.samplingRate
                + ", channelCount=" + DataActivity.channels.length
                + ", downsamplingOrder=" + downnsamplingOrder);

        worker = new Runnable() {
            @Override
            public void run() {
                try {
                    int samplesToRequest = DataActivity.samplingRate * windowSize;
                    double[][] tempArray = DataActivity.boardShim.get_current_board_data(samplesToRequest);
                    int availableSamples = tempArray.length == 0 ? 0 : tempArray[0].length;
                    if (availableSamples == 0) {
                        emptyDataLogCounter++;
                        if (emptyDataLogCounter == 1 || emptyDataLogCounter % 40 == 0) {
                            Log.i(TAG, "TimeSeries plot has no samples yet; stream is running but buffer is empty");
                        }
                    } else if (!firstSamplesLogged) {
                        firstSamplesLogged = true;
                        Log.i(TAG, "TimeSeries plot received samples: rows="
                                + tempArray.length + ", samples=" + availableSamples);
                    }
                    double[][] tmpArray = new double[tempArray.length][DataActivity.samplingRate * windowSize];
                    double[][] dataArray = null;
                    // prepend with zeroes if less datapoints
                    if (tempArray.length == 0) {
                        handler.postDelayed(this, timeSleep);
                        return;
                    }
                    if (tempArray[0].length != DataActivity.samplingRate * windowSize) {
                        for (int i = 0; i < tempArray.length; i++) {
                            for (int j = 0; j < DataActivity.samplingRate * windowSize - tempArray[i].length; j++) {
                                tmpArray[i][j] = 0.0;
                            }
                            for (int j = 0; j < tempArray[i].length; j++) {
                                tmpArray[i][j + DataActivity.samplingRate * windowSize - tempArray[i].length] = tempArray[i][j];
                            }
                        }
                    }
                    else {
                        tmpArray = tempArray;
                    }
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        DataFilter.perform_bandstop(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 48.0, 52.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                        DataFilter.perform_bandstop(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 58.0, 62.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                        DataFilter.perform_bandpass(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 2.0, 47.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                    }
                    // downsampling
                    if (downnsamplingOrder > 1) {
                        dataArray = new double[tmpArray.length][];
                        for (int i = 0; i < tmpArray.length; i++) {
                            dataArray[i] = DataFilter.perform_downsampling(tmpArray[i], downnsamplingOrder, AggOperations.MEDIAN.get_code());
                        }
                    }
                    else {
                        dataArray = tmpArray;
                    }
                    // prepare data to plot
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        int count = dataArray[DataActivity.channels[i]].length;
                        double[] channel = dataArray[DataActivity.channels[i]];
                        DataPoint[] values = new DataPoint[count];
                        // its a dirty hack but there is no simple way to create multiple plots dynamically,
                        // so use one plot and shift all values in channel by offset to make it look like multiple plots
                        int offset = 2 * maxValue * i;
                        for (int j = 0; j < count; j++) {
                            double x = j;
                            double y = channel[j];
                            if (y > maxValue) {
                                y = maxValue;
                            }
                            if (y < -maxValue) {
                                y = -maxValue;
                            }
                            y -= offset;
                            DataPoint v = new DataPoint(x, y);
                            values[j] = v;
                        }
                        series[i].resetData(values);
                    }
                } catch (Exception e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }

                handler.postDelayed(this, timeSleep);
            }
        };
        handler.postDelayed(worker, timeSleep);
    }

    @Override
    public void onPause() {
        if (worker != null) {
            handler.removeCallbacks(worker);
        }
        if (graph != null) {
            graph.removeAllSeries();
        }
        super.onPause();
    }
}
