package com.example.brainflowplot.ui.bandpowerplot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.example.brainflowplot.R;

public class BandPowerPlotFragment extends Fragment {

    private BandPowerPlotViewModel bandPowerPlotViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        bandPowerPlotViewModel =
                ViewModelProviders.of(this).get(BandPowerPlotViewModel.class);
        View root = inflater.inflate(R.layout.fragment_bandpowerplot, container, false);
        final TextView textView = root.findViewById(R.id.text_notifications);
        bandPowerPlotViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }
}