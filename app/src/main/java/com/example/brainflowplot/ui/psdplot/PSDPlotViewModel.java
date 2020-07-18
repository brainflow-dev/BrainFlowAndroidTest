package com.example.brainflowplot.ui.psdplot;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PSDPlotViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public PSDPlotViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}