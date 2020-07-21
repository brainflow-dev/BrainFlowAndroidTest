package com.example.brainflowplot.ui.psdplot;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.example.brainflowplot.DataActivity;
import com.example.brainflowplot.R;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.lang3.tuple.Pair;

import brainflow.BrainFlowError;
import brainflow.DataFilter;
import brainflow.FilterTypes;
import brainflow.WindowFunctions;


public class PSDPlotFragment extends Fragment {

    public int windowSize = 2;
    public int timeSleep = 100;
    private GraphView graph = null;
    private LineGraphSeries<DataPoint>[] series= null;

    private Runnable worker = null;
    private final Handler handler = new Handler();
    private double bandPassCenter = 0;
    private double bandPassWidth = 0;
    private double bandStopWidth = 0;
    private double bandStopCenter = 0;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_psdplot, container, false);

        graph = (GraphView) rootView.findViewById(R.id.psdgraph);
        graph.getViewport().setScalableY(true);
        graph.setTitle("Log10 PSD");
        graph.getGridLabelRenderer().setVerticalAxisTitle("g2/Hz");
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Hz");
        graph.getGridLabelRenderer().setVerticalLabelsVisible(true);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        graph.getViewport().setMinX(1);
        graph.getViewport().setMaxX(100);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(10);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setNumHorizontalLabels(10);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(rootView.getContext());
        String bp = prefs.getString(getString(R.string.bp_key), "1-50");
        String bs = prefs.getString(getString(R.string.bs_key), "58-62");
        int bpStart = Integer.valueOf(bp.split("-")[0]);
        int bpEnd = Integer.valueOf(bp.split("-")[1]);
        int bsStart = Integer.valueOf(bs.split("-")[0]);
        int bsEnd = Integer.valueOf(bs.split("-")[1]);

        bandStopWidth = Math.abs(bsEnd - bsStart);
        bandStopCenter = (bsStart + bsEnd) / 2.0;
        bandPassWidth = Math.abs(bpEnd - bpStart);
        bandPassCenter = (bpStart + bpEnd) / 2.0;

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        series = new LineGraphSeries[DataActivity.channels.length];

        for (int i = 0; i < DataActivity.channels.length; i++) {
            series[i] = new LineGraphSeries<DataPoint>();
            series[i].setColor(DataActivity.colors[i % DataActivity.colors.length]);
            graph.addSeries(series[i]);
        }

        worker = new Runnable() {
            @Override
            public void run() {
                try {
                    int numDataPoints = DataFilter.get_nearest_power_of_two(DataActivity.samplingRate * windowSize);
                    double[][] tmpArray = DataActivity.boardShim.get_current_board_data(numDataPoints);
                    if (tmpArray[0].length != numDataPoints) {
                        // dont prepend, just wait for more data
                        return;
                    }
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        DataFilter.perform_bandstop(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, bandStopCenter, bandStopWidth, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                        DataFilter.perform_bandpass(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, bandPassCenter, bandPassWidth, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                    }
                    // prepare data to plot
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        double[] channel = tmpArray[DataActivity.channels[i]];
                        Pair<double[], double[]> psd = DataFilter.get_log_psd (channel, 0, channel.length, DataActivity.samplingRate, WindowFunctions.HAMMING.get_code ());
                        int count = psd.getKey().length;
                        DataPoint[] values = new DataPoint[count];
                        for (int j = 0; j < count; j++) {
                            double x = psd.getValue()[j];
                            double y = psd.getKey()[j];
                            DataPoint v = new DataPoint(x, y);
                            values[j] = v;
                        }
                        series[i].resetData(values);
                    }
                } catch (BrainFlowError e) {
                    Log.e(getString(R.string.log_tag), e.getMessage());
                }

                handler.postDelayed(this, timeSleep);
            }
        };
        handler.postDelayed(worker, timeSleep);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(worker);
        graph.removeAllSeries();
        super.onPause();
    }
}