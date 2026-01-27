package com.dynamsoft.barcodebenchmark;

import androidx.lifecycle.ViewModel;
import com.dynamsoft.barcodebenchmark.server.BenchmarkWebServer;
import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends ViewModel {
    
    // Resolution setting: 0 = 720P, 1 = 1080P
    public int resolutionIndex = 0;
    
    // Benchmark mode: "camera", "image", "video"
    public String benchmarkMode = "camera";
    
    // Source file URI (for image/video modes)
    public String sourceFileUri;
    
    // Web server instance (persists across fragment recreations)
    public BenchmarkWebServer webServer;
    public boolean isWebServerRunning = false;
    
    // Dynamsoft benchmark results
    public BenchmarkResult dynamsoftResult;
    
    // MLkit benchmark results
    public BenchmarkResult mlkitResult;
    
    // Camera scan results (for real-time display)
    public List<BarcodeInfo> cameraScanResults = new ArrayList<>();
    
    public void reset() {
        dynamsoftResult = null;
        mlkitResult = null;
        cameraScanResults.clear();
        sourceFileUri = null;
    }
    
    public static class BenchmarkResult {
        public String engineName;
        public long totalTimeMs;
        public int framesProcessed;
        public List<BarcodeInfo> barcodes = new ArrayList<>();
        
        public BenchmarkResult(String engineName) {
            this.engineName = engineName;
        }
        
        public double getAvgTimePerFrame() {
            if (framesProcessed == 0) return 0;
            return (double) totalTimeMs / framesProcessed;
        }
        
        public int getTotalBarcodesFound() {
            return barcodes.size();
        }
        
        public int getUniqueBarcodeCount() {
            List<String> unique = new ArrayList<>();
            for (BarcodeInfo info : barcodes) {
                String key = info.format + ":" + info.text;
                if (!unique.contains(key)) {
                    unique.add(key);
                }
            }
            return unique.size();
        }
    }
    
    public static class BarcodeInfo {
        public String format;
        public String text;
        public long decodeTimeMs;
        public int frameIndex; // For video mode
        
        public BarcodeInfo(String format, String text, long decodeTimeMs) {
            this.format = format;
            this.text = text;
            this.decodeTimeMs = decodeTimeMs;
        }
        
        public BarcodeInfo(String format, String text, long decodeTimeMs, int frameIndex) {
            this(format, text, decodeTimeMs);
            this.frameIndex = frameIndex;
        }
        
        @Override
        public String toString() {
            return format + ": " + text;
        }
    }
}
