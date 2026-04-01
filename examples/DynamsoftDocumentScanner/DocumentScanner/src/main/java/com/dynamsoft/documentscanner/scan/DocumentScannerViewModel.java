package com.dynamsoft.documentscanner.scan;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DocumentScannerViewModel extends ViewModel {
    public MutableLiveData<String> actionBarTitle = new MutableLiveData<>("");
    public MutableLiveData<String> startCapturingError = new MutableLiveData<>(null);

    private final List<DocumentPage> pagesList = new ArrayList<>();
    public MutableLiveData<List<DocumentPage>> pages = new MutableLiveData<>(pagesList);
    public MutableLiveData<Integer> selectedPageIndex = new MutableLiveData<>(0);
    public MutableLiveData<Integer> retakePageIndex = new MutableLiveData<>(-1);

    public void addPage(DocumentPage page) {
        pagesList.add(page);
        pages.postValue(new ArrayList<>(pagesList));
    }

    public void removePage(int index) {
        if (index >= 0 && index < pagesList.size()) {
            pagesList.remove(index);
            pages.postValue(new ArrayList<>(pagesList));
        }
    }

    public void replacePage(int index, DocumentPage page) {
        if (index >= 0 && index < pagesList.size()) {
            pagesList.set(index, page);
            pages.postValue(new ArrayList<>(pagesList));
        }
    }

    public DocumentPage getPage(int index) {
        if (index >= 0 && index < pagesList.size()) {
            return pagesList.get(index);
        }
        return null;
    }

    public int getPageCount() {
        return pagesList.size();
    }

    public void swapPages(int from, int to) {
        if (from >= 0 && from < pagesList.size() && to >= 0 && to < pagesList.size()) {
            Collections.swap(pagesList, from, to);
            pages.postValue(new ArrayList<>(pagesList));
        }
    }

    public void reorderPages(List<DocumentPage> newOrder) {
        pagesList.clear();
        pagesList.addAll(newOrder);
        pages.postValue(new ArrayList<>(pagesList));
    }

    public List<DocumentPage> getPagesList() {
        return new ArrayList<>(pagesList);
    }

    public void notifyPagesChanged() {
        pages.postValue(new ArrayList<>(pagesList));
    }
}