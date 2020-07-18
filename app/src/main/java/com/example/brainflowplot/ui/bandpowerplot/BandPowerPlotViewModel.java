package com.example.brainflowplot.ui.bandpowerplot;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class BandPowerPlotViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public BandPowerPlotViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is notifications fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}