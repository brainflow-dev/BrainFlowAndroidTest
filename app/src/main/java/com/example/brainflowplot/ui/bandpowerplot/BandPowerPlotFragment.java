package com.example.brainflowplot.ui.bandpowerplot;

import android.graphics.Color;
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
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

import brainflow.BrainFlowError;
import brainflow.DataFilter;
import brainflow.WindowFunctions;


public class BandPowerPlotFragment extends Fragment {

    public int windowSize = 2;
    public int timeSleep = 100;
    private GraphView graph = null;
    private BarGraphSeries<DataPoint> series= null;

    private Runnable worker = null;
    private final Handler handler = new Handler();

    private List<Pair<Integer, Integer>> bands = new ArrayList<Pair<Integer, Integer>>();
    private final String[] names = {"Delta", "Theta", "Alpha", "Beta", "Gamma"};

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_bandpowerplot, container, false);

        graph = (GraphView) rootView.findViewById(R.id.bandpowergraph);
        graph.setTitle("BandPower");
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(5);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(1);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        series = new BarGraphSeries<DataPoint>();
        series.setValueDependentColor(new ValueDependentColor<DataPoint>() {
            @Override
            public int get(DataPoint data) {
                return Color.rgb((int) data.getX() * 255 / 6, (int) Math.abs(data.getY() * 255 / 4), 100);
            }
        });
        series.setTitle("Delta Theta Alpha Beta Gamma");

        graph.addSeries(series);

        bands.add(new ImmutablePair<Integer, Integer>(1, 4));
        bands.add(new ImmutablePair<Integer, Integer>(4, 8));
        bands.add(new ImmutablePair<Integer, Integer>(8, 13));
        bands.add(new ImmutablePair<Integer, Integer>(13, 30));
        bands.add(new ImmutablePair<Integer, Integer>(30, 70));

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

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
                    // average across all channels
                    DataPoint[] values = new DataPoint[bands.size()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = new DataPoint(i, 0);
                    }
                    for (int i = 0; i < DataActivity.channels.length; i++) {
                        double[] channel = tmpArray[DataActivity.channels[i]];
                        Pair<double[], double[]> psd = DataFilter.get_psd (channel, 0, channel.length, DataActivity.samplingRate, WindowFunctions.HAMMING.get_code ());
                        for (int j = 0; j < bands.size(); j++) {
                            double bandPower = DataFilter.get_band_power(psd, bands.get(j).getLeft(), bands.get(j).getRight());
                            values[j] = new DataPoint(values[j].getX(), values[j].getY() + bandPower);
                        }
                    }
                    // normalize for colors
                    double max = 0;
                    for (int j = 0; j < bands.size(); j++) {
                        if (values[j].getY() > max) {
                            max = values[j].getY();
                        }
                    }
                    for (int j = 0; j < bands.size(); j++) {
                        values[j] = new DataPoint(values[j].getX(), values[j].getY() / max);
                    }
                    series.resetData(values);
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