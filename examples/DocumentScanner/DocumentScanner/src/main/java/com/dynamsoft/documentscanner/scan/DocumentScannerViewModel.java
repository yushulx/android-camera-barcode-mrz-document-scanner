package com.dynamsoft.documentscanner.scan;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.basic_structures.Quadrilateral;

public class DocumentScannerViewModel extends ViewModel {
    public MutableLiveData<String> actionBarTitle = new MutableLiveData<>("");
    public MutableLiveData<String> startCapturingError = new MutableLiveData<>(null);

    public ImageData originalImage = null;
    public ImageData normalizedResultImage = null;
    public ImageData showingImage = null;
    public Quadrilateral resultLocation = null;
}