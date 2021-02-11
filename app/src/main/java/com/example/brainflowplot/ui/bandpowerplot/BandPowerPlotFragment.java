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

import org.apache.commons.lang3.tuple.Pair;

import brainflow.BrainFlowError;
import brainflow.DataFilter;


public class BandPowerPlotFragment extends Fragment {

    private int windowSize = 4;
    private int timeSleep = 50;
    private GraphView graph = null;
    private BarGraphSeries<DataPoint> series= null;
    private Runnable worker = null;
    private final Handler handler = new Handler();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_bandpowerplot, container, false);

        graph = (GraphView) rootView.findViewById(R.id.bandpowergraph);
        graph.setTitle("BandPowers");
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

        graph.addSeries(series);

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
                    Pair<double[], double[]> bands = DataFilter.get_avg_band_powers (tmpArray, DataActivity.channels, DataActivity.samplingRate, true);
                    DataPoint[] values = new DataPoint[bands.getKey().length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = new DataPoint(i, bands.getKey()[i]);
                    }
                    series.resetData(values);
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