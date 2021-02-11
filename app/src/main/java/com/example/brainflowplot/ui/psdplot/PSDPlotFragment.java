package com.example.brainflowplot.ui.psdplot;

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

import org.apache.commons.lang3.tuple.Pair;

import brainflow.BrainFlowError;
import brainflow.DataFilter;
import brainflow.FilterTypes;
import brainflow.WindowFunctions;


public class PSDPlotFragment extends Fragment {

    public int windowSize = 8;
    public int timeSleep = 50;
    private GraphView graph = null;
    private LineGraphSeries<DataPoint>[] series= null;

    private Runnable worker = null;
    private final Handler handler = new Handler();

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
        graph.getViewport().setMinY(-7);
        graph.getViewport().setMaxY(7);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getGridLabelRenderer().setNumHorizontalLabels(20);

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
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        DataFilter.perform_bandstop(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 50.0, 4.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                        DataFilter.perform_bandstop(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 60.0, 4.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                        DataFilter.perform_bandpass(tmpArray[DataActivity.channels[i]], DataActivity.samplingRate, 24.0, 47.0, 2,
                                FilterTypes.BUTTERWORTH.get_code (), 0.0);
                    }
                    // prepare data to plot
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        double[] channel = tmpArray[DataActivity.channels[i]];
                        int nfft = DataFilter.get_nearest_power_of_two(DataActivity.samplingRate) * 2;
                        Pair<double[], double[]> psd = DataFilter.get_psd_welch (channel, nfft, nfft / 2, DataActivity.samplingRate, WindowFunctions.HANNING.get_code());
                        int count = psd.getKey().length;
                        DataPoint[] values = new DataPoint[count];
                        for (int j = 0; j < count; j++) {
                            double x = psd.getValue()[j];
                            double y = psd.getKey()[j];
                            DataPoint v = new DataPoint(x, Math.log10(y));
                            values[j] = v;
                        }
                        series[i].resetData(values);
                    }
                } catch (BrainFlowError e) {
                    Log.e(getString(R.string.log_tag), Log.getStackTraceString(e));
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