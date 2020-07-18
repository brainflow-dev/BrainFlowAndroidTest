package com.example.brainflowplot.ui.dataplot;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DataPlotViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public DataPlotViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}