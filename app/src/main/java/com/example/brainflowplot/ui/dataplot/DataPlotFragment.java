package com.example.brainflowplot.ui.dataplot;

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

public class DataPlotFragment extends Fragment {

    private DataPlotViewModel dataPlotViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dataPlotViewModel =
                ViewModelProviders.of(this).get(DataPlotViewModel.class);
        View root = inflater.inflate(R.layout.fragment_dataplot, container, false);
        final TextView textView = root.findViewById(R.id.text_home);
        dataPlotViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        return root;
    }
}