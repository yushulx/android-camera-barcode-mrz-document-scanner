package com.dynamsoft.barcodebenchmark.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.dynamsoft.barcodebenchmark.BenchmarkConfig;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import fi.iki.elonen.NanoHTTPD;

import zxingcpp.BarcodeReader;

public class BenchmarkWebServer extends NanoHTTPD {

    private static final String TAG = "BenchmarkWebServer";
    private final Context context;
    private CaptureVisionRouter cvRouter;
    private BarcodeScanner mlkitScanner;
    private BarcodeReader zxingReader;
    private String uploadedTemplate = null;
    private String uploadedTemplateName = null; // extracted from CaptureVisionTemplates[0].Name
    private JSONObject annotationsData = null;

    public BenchmarkWebServer(Context context, int port) {
        super(port);
        this.context = context;
        initializeScanners();
    }

    private void initializeScanners() {
        try {
            cvRouter = new CaptureVisionRouter(context);
            if (BenchmarkConfig.USE_CUSTOM_TEMPLATE) {
                cvRouter.initSettings(BenchmarkConfig.DYNAMSOFT_TEMPLATE_JSON);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Dynamsoft CVR", e);
        }

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        mlkitScanner = BarcodeScanning.getClient(options);

        zxingReader = new BarcodeReader();
        zxingReader.getOptions().setTryHarder(true);
        zxingReader.getOptions().setTryRotate(true);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        try {
            if (uri.equals("/") || uri.equals("/index.html")) {
                return newFixedLengthResponse(Response.Status.OK, "text/html", getIndexHtml());
            } else if (uri.equals("/styles.css")) {
                return newFixedLengthResponse(Response.Status.OK, "text/css", getStylesCss());
            } else if (uri.equals("/app.js")) {
                return newFixedLengthResponse(Response.Status.OK, "application/javascript", getAppJs());
            } else if (uri.equals("/api/benchmark") && method == Method.POST) {
                return handleBenchmarkRequest(session);
            } else if (uri.equals("/api/template") && method == Method.POST) {
                return handleTemplateUpload(session);
            } else if (uri.equals("/api/template/clear") && method == Method.POST) {
                return handleTemplateClear();
            } else if (uri.equals("/api/annotations") && method == Method.POST) {
                return handleAnnotationsUpload(session);
            } else if (uri.equals("/api/config")) {
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"showBenchmarkTime\":true" +
                    ",\"hasTemplate\":" + (uploadedTemplate != null) +
                    ",\"hasAnnotations\":" + (annotationsData != null) + "}");
            } else if (uri.equals("/api/status")) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"status\":\"running\",\"dynamsoft\":" + (cvRouter != null) +
                    ",\"mlkit\":" + (mlkitScanner != null) +
                    ",\"zxing\":" + (zxingReader != null) + "}");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response handleBenchmarkRequest(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            Map<String, List<String>> params = session.getParameters();
            String fileType = params.containsKey("fileType") ? params.get("fileType").get(0) : "image";

            String tmpFilePath = files.get("file");
            if (tmpFilePath == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"No file uploaded\"}");
            }

            File tmpFile = new File(tmpFilePath);
            JSONObject result;

            if (fileType.equals("video")) {
                result = processVideo(tmpFile);
            } else {
                result = processImage(tmpFile);
            }

            tmpFile.delete();

            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", result.toString());
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;

        } catch (Exception e) {
            Log.e(TAG, "Error processing benchmark request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Response handleTemplateUpload(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tmpFilePath = files.get("file");
            if (tmpFilePath == null) {
                // Try reading as raw content from params
                Map<String, List<String>> params = session.getParameters();
                if (params.containsKey("content")) {
                    String content = params.get("content").get(0);
                    uploadedTemplate = content;
                    reinitDynamsoft();
                    Response r = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
                    r.addHeader("Access-Control-Allow-Origin", "*");
                    return r;
                }
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"No file uploaded\"}");
            }
            File f = new File(tmpFilePath);
            byte[] bytes = readFileBytes(f);
            f.delete();
            uploadedTemplate = new String(bytes, "UTF-8");
            // Extract the first CaptureVisionTemplates entry name, same logic as main.js
            try {
                org.json.JSONObject tpl = new org.json.JSONObject(uploadedTemplate);
                org.json.JSONArray templates = tpl.optJSONArray("CaptureVisionTemplates");
                if (templates != null && templates.length() > 0) {
                    uploadedTemplateName = templates.getJSONObject(0).optString("Name", null);
                } else {
                    org.json.JSONObject single = tpl.optJSONObject("CaptureVisionTemplate");
                    if (single != null) uploadedTemplateName = single.optString("Name", null);
                }
            } catch (Exception ex) {
                uploadedTemplateName = null;
            }
            reinitDynamsoft();
            String tplName = uploadedTemplateName != null ? uploadedTemplateName : "(unknown)";
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"success\":true,\"templateName\":\"" + tplName + "\"}");
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        } catch (Exception e) {
            Log.e(TAG, "Error handling template upload", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Response handleAnnotationsUpload(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tmpFilePath = files.get("file");
            if (tmpFilePath == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        "{\"error\":\"No file uploaded\"}");
            }
            File f = new File(tmpFilePath);
            byte[] bytes = readFileBytes(f);
            f.delete();
            String json = new String(bytes, "UTF-8");
            annotationsData = new JSONObject(json);
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        } catch (Exception e) {
            Log.e(TAG, "Error handling annotations upload", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Response handleTemplateClear() {
        uploadedTemplate = null;
        uploadedTemplateName = null;
        try {
            if (cvRouter != null) {
                cvRouter.resetSettings();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset Dynamsoft settings", e);
        }
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private void reinitDynamsoft() {
        try {
            if (cvRouter == null) {
                cvRouter = new CaptureVisionRouter(context);
            }
            if (uploadedTemplate != null) {
                cvRouter.initSettings(uploadedTemplate);
            } else {
                cvRouter.resetSettings();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reinit Dynamsoft with template", e);
        }
    }

    private byte[] readFileBytes(File f) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        fis.read(buf);
        fis.close();
        return buf;
    }

    private JSONObject processImage(File imageFile) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap == null) {
            throw new Exception("Failed to decode image");
        }

        JSONObject result = new JSONObject();
        result.put("type", "image");
        result.put("width", bitmap.getWidth());
        result.put("height", bitmap.getHeight());

        // Dynamsoft benchmark
        JSONObject dynamsoftResult = runDynamsoftBenchmark(bitmap);
        result.put("dynamsoft", dynamsoftResult);

        // MLkit benchmark
        JSONObject mlkitResult = runMLkitBenchmark(bitmap);
        result.put("mlkit", mlkitResult);

        // ZXing-CPP benchmark
        JSONObject zxingResult = runZXingCppBenchmark(bitmap);
        result.put("zxing", zxingResult);

        bitmap.recycle();
        return result;
    }

    private JSONObject processVideo(File videoFile) throws Exception {
        JSONObject result = new JSONObject();
        result.put("type", "video");

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoFile.getAbsolutePath());

        String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = durationStr != null ? Long.parseLong(durationStr) : 0;
        result.put("durationMs", duration);

        // Extract frames at 500ms intervals
        List<Bitmap> frames = new ArrayList<>();
        long interval = 500000; // microseconds
        for (long time = 0; time < duration * 1000; time += interval) {
            Bitmap frame = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null) {
                frames.add(frame);
            }
        }
        retriever.release();

        result.put("framesExtracted", frames.size());

        // Dynamsoft benchmark on all frames
        JSONObject dynamsoftResult = runDynamsoftVideoBenchmark(frames);
        result.put("dynamsoft", dynamsoftResult);

        // MLkit benchmark on all frames
        JSONObject mlkitResult = runMLkitVideoBenchmark(frames);
        result.put("mlkit", mlkitResult);

        // ZXing-CPP benchmark on all frames
        JSONObject zxingResult = runZXingCppVideoBenchmark(frames);
        result.put("zxing", zxingResult);

        // Cleanup
        for (Bitmap frame : frames) {
            frame.recycle();
        }

        return result;
    }

    private JSONObject runDynamsoftBenchmark(Bitmap bitmap) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();

        if (cvRouter == null) {
            result.put("error", "Dynamsoft not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            return result;
        }

        String templateName = (uploadedTemplateName != null) ? uploadedTemplateName : EnumPresetTemplate.PT_READ_BARCODES;
        long startTime = System.currentTimeMillis();
        CapturedResult capturedResult = cvRouter.capture(bitmap, templateName);
        long endTime = System.currentTimeMillis();

        result.put("timeMs", endTime - startTime);

        if (capturedResult != null) {
            DecodedBarcodesResult barcodesResult = capturedResult.getDecodedBarcodesResult();
            if (barcodesResult != null && barcodesResult.getItems() != null) {
                for (BarcodeResultItem item : barcodesResult.getItems()) {
                    JSONObject barcode = new JSONObject();
                    barcode.put("format", item.getFormatString());
                    barcode.put("text", item.getText());
                    barcodes.put(barcode);
                }
            }
        }

        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        return result;
    }

    private JSONObject runMLkitBenchmark(Bitmap bitmap) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();

        if (mlkitScanner == null) {
            result.put("error", "MLkit not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            return result;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        long startTime = System.currentTimeMillis();
        List<Barcode> detected = Tasks.await(mlkitScanner.process(image));
        long endTime = System.currentTimeMillis();

        result.put("timeMs", endTime - startTime);

        if (detected != null) {
            for (Barcode barcode : detected) {
                JSONObject bc = new JSONObject();
                bc.put("format", getBarcodeFormatName(barcode.getFormat()));
                bc.put("text", barcode.getRawValue());
                barcodes.put(bc);
            }
        }

        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        return result;
    }

    private JSONObject runDynamsoftVideoBenchmark(List<Bitmap> frames) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();
        Set<String> uniqueBarcodes = new HashSet<>();
        long totalTime = 0;

        if (cvRouter == null) {
            result.put("error", "Dynamsoft not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            result.put("framesProcessed", 0);
            return result;
        }

        for (int i = 0; i < frames.size(); i++) {
            Bitmap frame = frames.get(i);
            
            String templateName = (uploadedTemplateName != null) ? uploadedTemplateName : EnumPresetTemplate.PT_READ_BARCODES;
            long startTime = System.currentTimeMillis();
            CapturedResult capturedResult = cvRouter.capture(frame, templateName);
            long endTime = System.currentTimeMillis();
            totalTime += (endTime - startTime);

            if (capturedResult != null) {
                DecodedBarcodesResult barcodesResult = capturedResult.getDecodedBarcodesResult();
                if (barcodesResult != null && barcodesResult.getItems() != null) {
                    for (BarcodeResultItem item : barcodesResult.getItems()) {
                        String key = item.getFormatString() + ":" + item.getText();
                        if (!uniqueBarcodes.contains(key)) {
                            uniqueBarcodes.add(key);
                            JSONObject barcode = new JSONObject();
                            barcode.put("format", item.getFormatString());
                            barcode.put("text", item.getText());
                            barcode.put("frame", i + 1);
                            barcodes.put(barcode);
                        }
                    }
                }
            }
        }

        result.put("timeMs", totalTime);
        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        result.put("framesProcessed", frames.size());
        return result;
    }

    private JSONObject runMLkitVideoBenchmark(List<Bitmap> frames) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();
        Set<String> uniqueBarcodes = new HashSet<>();
        long totalTime = 0;

        if (mlkitScanner == null) {
            result.put("error", "MLkit not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            result.put("framesProcessed", 0);
            return result;
        }

        for (int i = 0; i < frames.size(); i++) {
            Bitmap frame = frames.get(i);
            InputImage image = InputImage.fromBitmap(frame, 0);

            long startTime = System.currentTimeMillis();
            List<Barcode> detected = Tasks.await(mlkitScanner.process(image));
            long endTime = System.currentTimeMillis();
            totalTime += (endTime - startTime);

            if (detected != null) {
                for (Barcode barcode : detected) {
                    String key = getBarcodeFormatName(barcode.getFormat()) + ":" + barcode.getRawValue();
                    if (!uniqueBarcodes.contains(key)) {
                        uniqueBarcodes.add(key);
                        JSONObject bc = new JSONObject();
                        bc.put("format", getBarcodeFormatName(barcode.getFormat()));
                        bc.put("text", barcode.getRawValue());
                        bc.put("frame", i + 1);
                        barcodes.put(bc);
                    }
                }
            }
        }

        result.put("timeMs", totalTime);
        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        result.put("framesProcessed", frames.size());
        return result;
    }

    private JSONObject runZXingCppBenchmark(Bitmap bitmap) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();

        if (zxingReader == null) {
            result.put("error", "ZXing-C++ not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            return result;
        }

        long startTime = System.currentTimeMillis();
        List<BarcodeReader.Result> detected = zxingReader.read(bitmap, new android.graphics.Rect(), 0);
        long endTime = System.currentTimeMillis();

        result.put("timeMs", endTime - startTime);

        if (detected != null) {
            for (BarcodeReader.Result item : detected) {
                JSONObject bc = new JSONObject();
                bc.put("format", item.getFormat().name());
                bc.put("text", item.getText() != null ? item.getText() : "");
                barcodes.put(bc);
            }
        }

        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        return result;
    }

    private JSONObject runZXingCppVideoBenchmark(List<Bitmap> frames) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray barcodes = new JSONArray();
        Set<String> uniqueBarcodes = new HashSet<>();
        long totalTime = 0;

        if (zxingReader == null) {
            result.put("error", "ZXing-C++ not initialized");
            result.put("timeMs", 0);
            result.put("barcodes", barcodes);
            result.put("framesProcessed", 0);
            return result;
        }

        for (int i = 0; i < frames.size(); i++) {
            Bitmap frame = frames.get(i);
            long startTime = System.currentTimeMillis();
            List<BarcodeReader.Result> detected = zxingReader.read(frame, new android.graphics.Rect(), 0);
            long endTime = System.currentTimeMillis();
            totalTime += (endTime - startTime);

            if (detected != null) {
                for (BarcodeReader.Result item : detected) {
                    String format = item.getFormat().name();
                    String text = item.getText() != null ? item.getText() : "";
                    String key = format + ":" + text;
                    if (!uniqueBarcodes.contains(key)) {
                        uniqueBarcodes.add(key);
                        JSONObject bc = new JSONObject();
                        bc.put("format", format);
                        bc.put("text", text);
                        bc.put("frame", i + 1);
                        barcodes.put(bc);
                    }
                }
            }
        }

        result.put("timeMs", totalTime);
        result.put("barcodes", barcodes);
        result.put("count", barcodes.length());
        result.put("framesProcessed", frames.size());
        return result;
    }

    private String getBarcodeFormatName(int format) {
        switch (format) {
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_CODE_93: return "CODE_93";
            case Barcode.FORMAT_CODABAR: return "CODABAR";
            case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_ITF: return "ITF";
            case Barcode.FORMAT_QR_CODE: return "QR_CODE";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            case Barcode.FORMAT_PDF417: return "PDF417";
            case Barcode.FORMAT_AZTEC: return "AZTEC";
            default: return "UNKNOWN";
        }
    }

    public void cleanup() {
        if (mlkitScanner != null) {
            mlkitScanner.close();
        }
    }

    private String getIndexHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Barcode Benchmark</title>\n" +
                "    <link rel=\"stylesheet\" href=\"/styles.css\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <header>\n" +
                "            <h1>Barcode Benchmark</h1>\n" +
                "            <p>Compare Dynamsoft vs MLkit vs ZXing-C++</p>\n" +
                "        </header>\n" +
                "\n" +
                "        <!-- Template Upload Section -->\n" +
                "        <div class=\"section-card\">\n" +
                "            <h3>Dynamsoft Template (optional)</h3>\n" +
                "            <p class=\"section-desc\">Upload a Dynamsoft Barcode Reader JSON template to customize detection settings.</p>\n" +
                "            <div class=\"row-group\">\n" +
                "                <label class=\"file-label\" for=\"templateInput\">Choose Template File (.json)</label>\n" +
                "                <input type=\"file\" id=\"templateInput\" accept=\".json,application/json\" style=\"display:none\">\n" +
                "                <span id=\"templateStatus\" class=\"status-text\">No template loaded</span>\n" +
                "                <button class=\"small-btn\" id=\"clearTemplateBtn\" style=\"display:none\" onclick=\"clearTemplate()\">Clear</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Annotations Upload Section -->\n" +
                "        <div class=\"section-card\">\n" +
                "            <h3>Ground Truth Annotations (optional)</h3>\n" +
                "            <p class=\"section-desc\">Upload annotations.json to enable GT metrics (detection rate &amp; precision) in the report.</p>\n" +
                "            <div class=\"row-group\">\n" +
                "                <label class=\"file-label\" for=\"annotationInput\">Choose Annotations File (.json)</label>\n" +
                "                <input type=\"file\" id=\"annotationInput\" accept=\".json,application/json\" style=\"display:none\">\n" +
                "                <span id=\"annotationStatus\" class=\"status-text\">No annotations loaded</span>\n" +
                "                <button class=\"small-btn\" id=\"clearAnnotationBtn\" style=\"display:none\" onclick=\"clearAnnotations()\">Clear</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- File Upload & Benchmark -->\n" +
                "        <div class=\"upload-section\">\n" +
                "            <div class=\"file-type-selector\">\n" +
                "                <label><input type=\"radio\" name=\"fileType\" value=\"image\" checked> <span class=\"radio-btn\">Images</span></label>\n" +
                "                <label><input type=\"radio\" name=\"fileType\" value=\"video\"> <span class=\"radio-btn\">Video</span></label>\n" +
                "            </div>\n" +
                "            <div class=\"drop-zone\" id=\"dropZone\">\n" +
                "                <div class=\"drop-zone-content\">\n" +
                "                    <span class=\"drop-icon\">&#128193;</span>\n" +
                "                    <p>Drag &amp; drop files or folders here</p>\n" +
                "                    <p class=\"hint\">Supports multiple images or a folder</p>\n" +
                "                    <p class=\"or\">or</p>\n" +
                "                    <button class=\"browse-btn\" id=\"browseBtn\">Browse Files</button>\n" +
                "                </div>\n" +
                "                <input type=\"file\" id=\"fileInput\" accept=\"image/*,video/*\" multiple hidden>\n" +
                "            </div>\n" +
                "            <div class=\"file-list\" id=\"fileList\" style=\"display:none;\">\n" +
                "                <div class=\"file-list-header\">\n" +
                "                    <span id=\"fileCount\">0 files selected</span>\n" +
                "                    <button class=\"clear-btn\" id=\"clearBtn\">Clear All</button>\n" +
                "                </div>\n" +
                "                <div class=\"file-items\" id=\"fileItems\"></div>\n" +
                "            </div>\n" +
                "            <button class=\"benchmark-btn\" id=\"benchmarkBtn\" disabled>Run Benchmark</button>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"progress-section\" id=\"progressSection\" style=\"display:none;\">\n" +
                "            <div class=\"progress-header\">\n" +
                "                <span id=\"progressText\">Processing...</span>\n" +
                "                <span id=\"progressCount\">0/0</span>\n" +
                "            </div>\n" +
                "            <div class=\"progress-bar\"><div class=\"progress-fill\" id=\"progressFill\"></div></div>\n" +
                "            <div class=\"current-file\" id=\"currentFile\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"results\" id=\"results\" style=\"display:none;\">\n" +
                "            <div class=\"results-header\">\n" +
                "                <h2>Benchmark Results</h2>\n" +
                "                <button class=\"export-btn\" id=\"exportBtn\" onclick=\"exportReport()\">Export Report</button>\n" +
                "            </div>\n" +
                "            <div id=\"batchSummary\"></div>\n" +
                "            <div id=\"batchResults\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <footer><p>Dynamsoft Barcode Reader &bull; Google MLkit &bull; ZXing-C++</p></footer>\n" +
                "    </div>\n" +
                "    <script src=\"/app.js\"></script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String getStylesCss() {
        return "* {\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "    box-sizing: border-box;\n" +
                "}\n" +
                "\n" +
                "body {\n" +
                "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n" +
                "    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);\n" +
                "    min-height: 100vh;\n" +
                "    color: #fff;\n" +
                "}\n" +
                "\n" +
                ".container {\n" +
                "    max-width: 1000px;\n" +
                "    margin: 0 auto;\n" +
                "    padding: 40px 20px;\n" +
                "}\n" +
                "\n" +
                "header {\n" +
                "    text-align: center;\n" +
                "    margin-bottom: 40px;\n" +
                "}\n" +
                "\n" +
                "header h1 {\n" +
                "    font-size: 2.5rem;\n" +
                "    margin-bottom: 10px;\n" +
                "    background: linear-gradient(90deg, #4facfe, #00f2fe);\n" +
                "    -webkit-background-clip: text;\n" +
                "    -webkit-text-fill-color: transparent;\n" +
                "}\n" +
                "\n" +
                "header p {\n" +
                "    color: #888;\n" +
                "    font-size: 1.1rem;\n" +
                "}\n" +
                "\n" +
                ".upload-section {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    border-radius: 20px;\n" +
                "    padding: 30px;\n" +
                "    margin-bottom: 30px;\n" +
                "}\n" +
                "\n" +
                ".file-type-selector {\n" +
                "    display: flex;\n" +
                "    gap: 15px;\n" +
                "    justify-content: center;\n" +
                "    margin-bottom: 25px;\n" +
                "}\n" +
                "\n" +
                ".file-type-selector label { cursor: pointer; }\n" +
                ".file-type-selector input { display: none; }\n" +
                "\n" +
                ".radio-btn {\n" +
                "    display: inline-block;\n" +
                "    padding: 12px 30px;\n" +
                "    background: rgba(255,255,255,0.1);\n" +
                "    border-radius: 30px;\n" +
                "    transition: all 0.3s;\n" +
                "}\n" +
                "\n" +
                ".file-type-selector input:checked + .radio-btn {\n" +
                "    background: linear-gradient(90deg, #4facfe, #00f2fe);\n" +
                "    color: #1a1a2e;\n" +
                "    font-weight: 600;\n" +
                "}\n" +
                "\n" +
                ".drop-zone {\n" +
                "    border: 2px dashed rgba(255,255,255,0.3);\n" +
                "    border-radius: 15px;\n" +
                "    padding: 50px;\n" +
                "    text-align: center;\n" +
                "    transition: all 0.3s;\n" +
                "    cursor: pointer;\n" +
                "}\n" +
                "\n" +
                ".drop-zone:hover, .drop-zone.dragover {\n" +
                "    border-color: #4facfe;\n" +
                "    background: rgba(79,172,254,0.1);\n" +
                "}\n" +
                "\n" +
                ".drop-icon { font-size: 3rem; display: block; margin-bottom: 15px; }\n" +
                ".drop-zone p { color: #888; margin-bottom: 10px; }\n" +
                ".hint { font-size: 0.85rem !important; color: #666 !important; }\n" +
                ".or { color: #555; font-size: 0.9rem; }\n" +
                "\n" +
                ".browse-btn {\n" +
                "    background: linear-gradient(90deg, #4facfe, #00f2fe);\n" +
                "    border: none;\n" +
                "    padding: 12px 30px;\n" +
                "    border-radius: 30px;\n" +
                "    color: #1a1a2e;\n" +
                "    font-weight: 600;\n" +
                "    cursor: pointer;\n" +
                "    margin-top: 10px;\n" +
                "    transition: transform 0.2s;\n" +
                "}\n" +
                "\n" +
                ".browse-btn:hover { transform: scale(1.05); }\n" +
                "\n" +
                ".file-list {\n" +
                "    margin-top: 20px;\n" +
                "    background: rgba(0,0,0,0.3);\n" +
                "    border-radius: 10px;\n" +
                "    overflow: hidden;\n" +
                "}\n" +
                "\n" +
                ".file-list-header {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    align-items: center;\n" +
                "    padding: 15px;\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    border-bottom: 1px solid rgba(255,255,255,0.1);\n" +
                "}\n" +
                "\n" +
                ".clear-btn {\n" +
                "    background: #ff4757;\n" +
                "    border: none;\n" +
                "    padding: 8px 16px;\n" +
                "    border-radius: 5px;\n" +
                "    color: white;\n" +
                "    cursor: pointer;\n" +
                "    font-size: 0.85rem;\n" +
                "}\n" +
                "\n" +
                ".file-items {\n" +
                "    max-height: 200px;\n" +
                "    overflow-y: auto;\n" +
                "    padding: 10px;\n" +
                "}\n" +
                "\n" +
                ".file-item {\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "    gap: 10px;\n" +
                "    padding: 8px 12px;\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    border-radius: 6px;\n" +
                "    margin-bottom: 6px;\n" +
                "    font-size: 0.9rem;\n" +
                "}\n" +
                "\n" +
                ".file-item-icon { font-size: 1.2rem; }\n" +
                ".file-item-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n" +
                ".file-item-size { color: #888; font-size: 0.8rem; }\n" +
                ".file-item-status { font-size: 1rem; }\n" +
                "\n" +
                ".benchmark-btn {\n" +
                "    width: 100%;\n" +
                "    padding: 18px;\n" +
                "    margin-top: 25px;\n" +
                "    background: linear-gradient(90deg, #f093fb, #f5576c);\n" +
                "    border: none;\n" +
                "    border-radius: 10px;\n" +
                "    color: white;\n" +
                "    font-size: 1.1rem;\n" +
                "    font-weight: 600;\n" +
                "    cursor: pointer;\n" +
                "    transition: all 0.3s;\n" +
                "}\n" +
                "\n" +
                ".benchmark-btn:disabled { opacity: 0.5; cursor: not-allowed; }\n" +
                ".benchmark-btn:not(:disabled):hover { transform: translateY(-2px); box-shadow: 0 10px 30px rgba(240,147,251,0.3); }\n" +
                "\n" +
                ".progress-section {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    border-radius: 15px;\n" +
                "    padding: 25px;\n" +
                "    margin-bottom: 30px;\n" +
                "}\n" +
                "\n" +
                ".progress-header {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    margin-bottom: 15px;\n" +
                "}\n" +
                "\n" +
                ".progress-bar {\n" +
                "    height: 8px;\n" +
                "    background: rgba(255,255,255,0.1);\n" +
                "    border-radius: 4px;\n" +
                "    overflow: hidden;\n" +
                "}\n" +
                "\n" +
                ".progress-fill {\n" +
                "    height: 100%;\n" +
                "    background: linear-gradient(90deg, #4facfe, #00f2fe);\n" +
                "    width: 0%;\n" +
                "    transition: width 0.3s;\n" +
                "}\n" +
                "\n" +
                ".current-file {\n" +
                "    margin-top: 10px;\n" +
                "    font-size: 0.9rem;\n" +
                "    color: #888;\n" +
                "}\n" +
                "\n" +
                ".results {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    border-radius: 20px;\n" +
                "    padding: 30px;\n" +
                "}\n" +
                "\n" +
                ".results h2 { text-align: center; margin-bottom: 25px; }\n" +
                "\n" +
                ".batch-summary {\n" +
                "    display: grid;\n" +
                "    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));\n" +
                "    gap: 15px;\n" +
                "    margin-bottom: 25px;\n" +
                "}\n" +
                "\n" +
                ".summary-card {\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    padding: 20px;\n" +
                "    border-radius: 10px;\n" +
                "    text-align: center;\n" +
                "}\n" +
                "\n" +
                ".summary-card .value { font-size: 2rem; font-weight: 700; }\n" +
                ".summary-card .label { font-size: 0.85rem; color: #888; margin-top: 5px; }\n" +
                ".summary-card.dynamsoft .value { color: #2196F3; }\n" +
                ".summary-card.mlkit .value { color: #4CAF50; }\n" +
                "\n" +
                ".batch-results { }\n" +
                "\n" +
                ".result-item {\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    border-radius: 12px;\n" +
                "    margin-bottom: 15px;\n" +
                "    overflow: hidden;\n" +
                "}\n" +
                "\n" +
                ".result-item-header {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    align-items: center;\n" +
                "    padding: 15px 20px;\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    cursor: pointer;\n" +
                "}\n" +
                "\n" +
                ".result-item-header:hover { background: rgba(255,255,255,0.08); }\n" +
                ".result-item-name { font-weight: 600; }\n" +
                ".result-item-stats { display: flex; gap: 20px; font-size: 0.9rem; }\n" +
                ".result-item-stats .dynamsoft { color: #2196F3; }\n" +
                ".result-item-stats .mlkit { color: #4CAF50; }\n" +
                "\n" +
                ".result-item-details {\n" +
                "    display: none;\n" +
                "    padding: 20px;\n" +
                "}\n" +
                "\n" +
                ".result-item.expanded .result-item-details { display: block; }\n" +
                "\n" +
                ".comparison {\n" +
                "    display: grid;\n" +
                "    grid-template-columns: 1fr 1fr;\n" +
                "    gap: 20px;\n" +
                "}\n" +
                "\n" +
                "@media (max-width: 700px) { .comparison { grid-template-columns: 1fr; } }\n" +
                "\n" +
                ".sdk-result {\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    border-radius: 10px;\n" +
                "    padding: 15px;\n" +
                "}\n" +
                "\n" +
                ".sdk-result.dynamsoft { border-top: 3px solid #2196F3; }\n" +
                ".sdk-result.mlkit { border-top: 3px solid #4CAF50; }\n" +
                "\n" +
                ".sdk-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }\n" +
                ".sdk-icon { font-size: 1.2rem; }\n" +
                ".sdk-header h4 { font-size: 0.95rem; }\n" +
                "\n" +
                ".sdk-stats { font-size: 0.85rem; margin-bottom: 10px; }\n" +
                ".stat-item { display: flex; justify-content: space-between; margin-bottom: 4px; }\n" +
                ".stat-label { color: #888; }\n" +
                ".stat-value { font-weight: 600; }\n" +
                "\n" +
                ".barcode-list { max-height: 150px; overflow-y: auto; }\n" +
                "\n" +
                ".barcode-item {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    padding: 8px;\n" +
                "    border-radius: 6px;\n" +
                "    margin-bottom: 6px;\n" +
                "    font-size: 0.8rem;\n" +
                "}\n" +
                "\n" +
                ".barcode-format { color: #4facfe; margin-bottom: 3px; }\n" +
                ".barcode-text { font-family: monospace; word-break: break-all; }\n" +
                "\n" +
                "footer { text-align: center; margin-top: 40px; color: #555; font-size: 0.9rem; }\n" +
                "\n" +
                ".expand-icon { transition: transform 0.3s; }\n" +
                ".result-item.expanded .expand-icon { transform: rotate(180deg); }\n" +
                "\n" +
                "/* Light theme overrides for new sections */\n" +
                ".section-card { background:#fff; border:1px solid #e2e8f0; border-radius:12px; padding:20px 24px; margin-bottom:20px; }\n" +
                ".section-card h3 { font-size:1rem; font-weight:700; color:#1e293b; margin-bottom:6px; }\n" +
                ".section-desc { font-size:0.85rem; color:#64748b; margin-bottom:12px; }\n" +
                ".row-group { display:flex; align-items:center; gap:12px; flex-wrap:wrap; }\n" +
                ".file-label { background:#e0f2fe; color:#0369a1; padding:7px 14px; border-radius:6px; cursor:pointer; font-size:0.85rem; font-weight:600; }\n" +
                ".file-label:hover { background:#bae6fd; }\n" +
                ".status-text { font-size:0.85rem; color:#64748b; }\n" +
                ".status-text.loaded { color:#16a34a; font-weight:600; }\n" +
                ".small-btn { background:#fee2e2; color:#dc2626; border:none; border-radius:6px; padding:5px 10px; font-size:0.8rem; cursor:pointer; font-weight:600; }\n" +
                ".small-btn:hover { background:#fecaca; }\n" +
                ".results-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }\n" +
                ".results-header h2 { color:#0f172a; }\n" +
                ".export-btn { background:#1e40af; color:#fff; border:none; border-radius:8px; padding:9px 18px; font-size:0.9rem; font-weight:600; cursor:pointer; }\n" +
                ".export-btn:hover { background:#1d4ed8; }\n" +
                ".benchmark-table-wrap { overflow-x:auto; margin-bottom:16px; }\n" +
                ".benchmark-table { width:100%; border-collapse:collapse; background:#fff; border-radius:8px; overflow:hidden; box-shadow:0 1px 4px rgba(0,0,0,.07); font-size:0.88rem; }\n" +
                ".benchmark-table th { background:#f1f5f9; color:#475569; font-weight:600; text-align:left; padding:10px 14px; border-bottom:1px solid #e2e8f0; }\n" +
                ".benchmark-table td { padding:9px 14px; border-bottom:1px solid #f1f5f9; vertical-align:top; color:#1e293b; }\n" +
                ".benchmark-table tr:last-child td { border-bottom:none; }\n" +
                ".benchmark-table tr:hover td { background:#f8fafc; }\n" +
                ".sdk-col { font-weight:600; white-space:nowrap; }\n" +
                ".count-col { text-align:center; font-weight:700; font-size:1.05em; }\n" +
                ".time-col { text-align:right; color:#64748b; white-space:nowrap; }\n" +
                ".rate-col { text-align:center; }\n" +
                ".best-count { color:#16a34a; }\n" +
                ".barcodes-list { list-style:none; padding:0; margin:0; }\n" +
                ".barcodes-list li { font-size:0.82rem; color:#334155; padding:2px 0; }\n" +
                ".gt-good { color:#16a34a; font-weight:700; }\n" +
                ".gt-ok { color:#ca8a04; font-weight:700; }\n" +
                ".gt-bad { color:#dc2626; font-weight:700; }\n" +
                ".benchmark-image-title { color:#1e293b; font-size:0.93rem; font-weight:700; margin:18px 0 6px; }\n" +
                ".benchmark-summary { background:#f8fafc; border:1px solid #e2e8f0; border-radius:10px; padding:16px 18px; margin-bottom:20px; }\n" +
                ".benchmark-summary h4 { color:#1e293b; font-size:0.95rem; margin-bottom:10px; }\n" +
                ".benchmark-summary ul { list-style:none; padding:0; margin:10px 0 0; }\n" +
                ".benchmark-summary ul li { font-size:0.88rem; color:#475569; padding:2px 0; }";
    }

    private String getAppJs() {
        return "// ===== Template Upload =====\n" +
                "let loadedTemplate = null;\n" +
                "const templateInput = document.getElementById('templateInput');\n" +
                "const templateStatus = document.getElementById('templateStatus');\n" +
                "const clearTemplateBtn = document.getElementById('clearTemplateBtn');\n" +
                "templateInput.addEventListener('change', (e) => {\n" +
                "    const file = e.target.files[0];\n" +
                "    if (!file) return;\n" +
                "    const reader = new FileReader();\n" +
                "    reader.onload = async (ev) => {\n" +
                "        loadedTemplate = ev.target.result;\n" +
                "        const fd = new FormData();\n" +
                "        fd.append('file', file);\n" +
                "        try {\n" +
                "            const resp = await fetch('/api/template', { method: 'POST', body: fd });\n" +
                "            const d = await resp.json();\n" +
                "            if (d.success) {\n" +
                "                const taskName = d.templateName || '(unknown)';\n" +
                "                templateStatus.textContent = '\\u2713 ' + file.name + ' (task: ' + taskName + ')';\n" +
                "                templateStatus.className = 'status-text loaded';\n" +
                "                clearTemplateBtn.style.display = 'inline-block';\n" +
                "            } else {\n" +
                "                templateStatus.textContent = 'Error: ' + (d.error || 'unknown');\n" +
                "            }\n" +
                "        } catch (err) {\n" +
                "            templateStatus.textContent = 'Upload failed: ' + err.message;\n" +
                "        }\n" +
                "    };\n" +
                "    reader.readAsText(file);\n" +
                "    templateInput.value = '';\n" +
                "});\n" +
                "function clearTemplate() {\n" +
                "    loadedTemplate = null;\n" +
                "    templateStatus.textContent = 'No template loaded';\n" +
                "    templateStatus.className = 'status-text';\n" +
                "    clearTemplateBtn.style.display = 'none';\n" +
                "    fetch('/api/template/clear', { method: 'POST' }).catch(() => {});\n" +
                "}\n" +
                "\n" +
                "// ===== Annotations Upload =====\n" +
                "let annotationData = null; // map: filename -> [{text, format}]\n" +
                "const annotationInput = document.getElementById('annotationInput');\n" +
                "const annotationStatus = document.getElementById('annotationStatus');\n" +
                "const clearAnnotationBtn = document.getElementById('clearAnnotationBtn');\n" +
                "annotationInput.addEventListener('change', (e) => {\n" +
                "    const file = e.target.files[0];\n" +
                "    if (!file) return;\n" +
                "    const reader = new FileReader();\n" +
                "    reader.onload = async (ev) => {\n" +
                "        try {\n" +
                "            const json = JSON.parse(ev.target.result);\n" +
                "            annotationData = {};\n" +
                "            if (json.images) {\n" +
                "                for (const entry of json.images) {\n" +
                "                    if (entry.file && entry.barcodes) {\n" +
                "                        annotationData[entry.file] = entry.barcodes;\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "            // Upload to server for server-side processing\n" +
                "            const fd = new FormData();\n" +
                "            fd.append('file', file);\n" +
                "            await fetch('/api/annotations', { method: 'POST', body: fd });\n" +
                "            const count = Object.keys(annotationData).length;\n" +
                "            const total = Object.values(annotationData).reduce((s, a) => s + a.length, 0);\n" +
                "            annotationStatus.textContent = count + ' images, ' + total + ' barcodes';\n" +
                "            annotationStatus.className = 'status-text loaded';\n" +
                "            clearAnnotationBtn.style.display = 'inline-block';\n" +
                "        } catch (err) {\n" +
                "            annotationStatus.textContent = 'Parse error: ' + err.message;\n" +
                "        }\n" +
                "    };\n" +
                "    reader.readAsText(file);\n" +
                "    annotationInput.value = '';\n" +
                "});\n" +
                "function clearAnnotations() {\n" +
                "    annotationData = null;\n" +
                "    annotationStatus.textContent = 'No annotations loaded';\n" +
                "    annotationStatus.className = 'status-text';\n" +
                "    clearAnnotationBtn.style.display = 'none';\n" +
                "}\n" +
                "\n" +
                "// ===== File Select & Drop =====\n" +
                "const dropZone = document.getElementById('dropZone');\n" +
                "const fileInput = document.getElementById('fileInput');\n" +
                "const browseBtn = document.getElementById('browseBtn');\n" +
                "const fileList = document.getElementById('fileList');\n" +
                "const fileItems = document.getElementById('fileItems');\n" +
                "const fileCount = document.getElementById('fileCount');\n" +
                "const clearBtn = document.getElementById('clearBtn');\n" +
                "const benchmarkBtn = document.getElementById('benchmarkBtn');\n" +
                "const progressSection = document.getElementById('progressSection');\n" +
                "const progressFill = document.getElementById('progressFill');\n" +
                "const progressText = document.getElementById('progressText');\n" +
                "const currentFile = document.getElementById('currentFile');\n" +
                "const results = document.getElementById('results');\n" +
                "const batchSummary = document.getElementById('batchSummary');\n" +
                "const batchResults = document.getElementById('batchResults');\n" +
                "\n" +
                "let selectedFiles = [];\n" +
                "let benchmarkResults = [];\n" +
                "let showBenchmarkTime = true;\n" +
                "\n" +
                "fetch('/api/config').then(r => r.json()).then(cfg => { showBenchmarkTime = cfg.showBenchmarkTime; }).catch(() => {});\n" +
                "\n" +
                "dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });\n" +
                "dropZone.addEventListener('dragleave', () => { dropZone.classList.remove('dragover'); });\n" +
                "dropZone.addEventListener('drop', async (e) => {\n" +
                "    e.preventDefault();\n" +
                "    dropZone.classList.remove('dragover');\n" +
                "    const items = e.dataTransfer.items;\n" +
                "    const files = [];\n" +
                "    const promises = [];\n" +
                "    for (let i = 0; i < items.length; i++) {\n" +
                "        const item = items[i];\n" +
                "        if (item.webkitGetAsEntry) {\n" +
                "            const entry = item.webkitGetAsEntry();\n" +
                "            if (entry) promises.push(traverseEntry(entry));\n" +
                "        } else if (item.getAsFile) {\n" +
                "            const file = item.getAsFile();\n" +
                "            if (file && isValidFile(file)) files.push(file);\n" +
                "        }\n" +
                "    }\n" +
                "    const nestedFiles = await Promise.all(promises);\n" +
                "    nestedFiles.flat().forEach(f => { if (isValidFile(f)) files.push(f); });\n" +
                "    addFiles(files);\n" +
                "});\n" +
                "\n" +
                "async function traverseEntry(entry) {\n" +
                "    if (entry.isFile) {\n" +
                "        return new Promise(resolve => { entry.file(file => resolve([file]), () => resolve([])); });\n" +
                "    } else if (entry.isDirectory) {\n" +
                "        const reader = entry.createReader();\n" +
                "        return new Promise(resolve => {\n" +
                "            reader.readEntries(async entries => {\n" +
                "                const files = [];\n" +
                "                for (const e of entries) { const sub = await traverseEntry(e); files.push(...sub); }\n" +
                "                resolve(files);\n" +
                "            }, () => resolve([]));\n" +
                "        });\n" +
                "    }\n" +
                "    return [];\n" +
                "}\n" +
                "\n" +
                "function isValidFile(f) { return f.type.startsWith('image/') || f.type.startsWith('video/'); }\n" +
                "function formatSize(b) {\n" +
                "    if (b < 1024) return b + ' B';\n" +
                "    if (b < 1048576) return (b/1024).toFixed(1) + ' KB';\n" +
                "    return (b/1048576).toFixed(1) + ' MB';\n" +
                "}\n" +
                "function addFiles(files) {\n" +
                "    files.forEach(f => { if (!selectedFiles.find(x => x.name === f.name && x.size === f.size)) selectedFiles.push(f); });\n" +
                "    updateFileList();\n" +
                "}\n" +
                "function updateFileList() {\n" +
                "    if (selectedFiles.length === 0) { fileList.style.display = 'none'; benchmarkBtn.disabled = true; return; }\n" +
                "    fileList.style.display = 'block';\n" +
                "    benchmarkBtn.disabled = false;\n" +
                "    fileCount.textContent = selectedFiles.length + ' file(s) selected';\n" +
                "    fileItems.innerHTML = selectedFiles.map((f, idx) => `<div class=\"file-item\" data-idx=\"${idx}\"><span>${f.type.startsWith('video/') ? '&#127909;' : '&#128444;'}</span><span class=\"file-item-name\">${f.name}</span><span class=\"file-item-size\">${formatSize(f.size)}</span><span class=\"file-item-status\" id=\"status-${idx}\"></span></div>`).join('');\n" +
                "}\n" +
                "\n" +
                "dropZone.addEventListener('click', () => fileInput.click());\n" +
                "browseBtn.addEventListener('click', (e) => { e.stopPropagation(); fileInput.click(); });\n" +
                "fileInput.addEventListener('change', (e) => { addFiles(Array.from(e.target.files)); fileInput.value = ''; });\n" +
                "clearBtn.addEventListener('click', () => { selectedFiles = []; updateFileList(); results.style.display = 'none'; progressSection.style.display = 'none'; });\n" +
                "\n" +
                "// ===== Benchmark Execution =====\n" +
                "benchmarkBtn.addEventListener('click', async () => {\n" +
                "    if (selectedFiles.length === 0) return;\n" +
                "    progressSection.style.display = 'block';\n" +
                "    results.style.display = 'none';\n" +
                "    benchmarkBtn.disabled = true;\n" +
                "    benchmarkResults = [];\n" +
                "    for (let i = 0; i < selectedFiles.length; i++) {\n" +
                "        const file = selectedFiles[i];\n" +
                "        progressFill.style.width = Math.round(i / selectedFiles.length * 100) + '%';\n" +
                "        progressText.textContent = (i + 1) + ' / ' + selectedFiles.length;\n" +
                "        currentFile.textContent = 'Processing: ' + file.name;\n" +
                "        const statusEl = document.getElementById('status-' + i);\n" +
                "        if (statusEl) statusEl.textContent = '\u23f3';\n" +
                "        try {\n" +
                "            const fd = new FormData();\n" +
                "            fd.append('file', file);\n" +
                "            fd.append('fileType', file.type.startsWith('video/') ? 'video' : 'image');\n" +
                "            const resp = await fetch('/api/benchmark', { method: 'POST', body: fd });\n" +
                "            const data = await resp.json();\n" +
                "            data.fileName = file.name;\n" +
                "            benchmarkResults.push(data);\n" +
                "            if (statusEl) statusEl.textContent = '\u2713';\n" +
                "        } catch (err) {\n" +
                "            benchmarkResults.push({ fileName: file.name, error: err.message });\n" +
                "            if (statusEl) statusEl.textContent = '\u2717';\n" +
                "        }\n" +
                "    }\n" +
                "    progressFill.style.width = '100%';\n" +
                "    progressText.textContent = 'Complete!';\n" +
                "    currentFile.textContent = '';\n" +
                "    benchmarkBtn.disabled = false;\n" +
                "    displayBatchResults();\n" +
                "});\n" +
                "\n" +
                "// ===== GT Helpers =====\n" +
                "function computeGTResult(detectedTexts, groundTruth) {\n" +
                "    if (!groundTruth || groundTruth.length === 0) return null;\n" +
                "    const gtTexts = groundTruth.map(b => (b.text || b).trim().toLowerCase());\n" +
                "    const detLower = detectedTexts.map(t => (t || '').trim().toLowerCase());\n" +
                "    let tp = 0;\n" +
                "    const matched = new Array(gtTexts.length).fill(false);\n" +
                "    for (const det of detLower) {\n" +
                "        const idx = gtTexts.findIndex((g, i) => !matched[i] && g === det);\n" +
                "        if (idx >= 0) { matched[idx] = true; tp++; }\n" +
                "    }\n" +
                "    const fp = detectedTexts.length - tp;\n" +
                "    const total = gtTexts.length;\n" +
                "    const detectionRate = total > 0 ? tp / total : 0;\n" +
                "    const precision = (tp + fp) > 0 ? tp / (tp + fp) : 0;\n" +
                "    return { tp, fp, total, detectionRate, precision };\n" +
                "}\n" +
                "\n" +
                "function gtRateClass(rate) {\n" +
                "    if (rate >= 0.9) return 'gt-good';\n" +
                "    if (rate >= 0.6) return 'gt-ok';\n" +
                "    return 'gt-bad';\n" +
                "}\n" +
                "\n" +
                "function escapeHtml(s) {\n" +
                "    const d = document.createElement('div'); d.textContent = s; return d.innerHTML;\n" +
                "}\n" +
                "\n" +
                "// ===== Display Results =====\n" +
                "function displayBatchResults() {\n" +
                "    results.style.display = 'block';\n" +
                "    const sdkKeys = ['dynamsoft', 'mlkit', 'zxing'];\n" +
                "    const sdkLabels = { dynamsoft: 'Dynamsoft', mlkit: 'Google MLkit', zxing: 'ZXing-C++' };\n" +
                "    const allResults = benchmarkResults.filter(r => !r.error).map(r => {\n" +
                "        const gt = annotationData && annotationData[r.fileName];\n" +
                "        return {\n" +
                "            imageName: r.fileName,\n" +
                "            sdkResults: sdkKeys.map(key => {\n" +
                "                const sdk = r[key] || { count: 0, barcodes: [] };\n" +
                "                const detectedTexts = (sdk.barcodes || []).map(b => b.text || '');\n" +
                "                const gtResult = gt ? computeGTResult(detectedTexts, gt) : null;\n" +
                "                return { sdkLabel: sdkLabels[key], barcodes: sdk.barcodes || [], time: sdk.timeMs || 0, error: sdk.error, gtResult };\n" +
                "            })\n" +
                "        };\n" +
                "    });\n" +
                "\n" +
                "    batchSummary.innerHTML = buildSummaryHtml(allResults, sdkKeys, sdkLabels);\n" +
                "    batchResults.innerHTML = allResults.map(r => buildImageTableHtml(r)).join('');\n" +
                "\n" +
                "    // Errors\n" +
                "    const errors = benchmarkResults.filter(r => r.error);\n" +
                "    if (errors.length > 0) {\n" +
                "        batchResults.innerHTML += errors.map(r => '<div style=\"color:#dc2626;margin:8px 0;\">Error in ' + escapeHtml(r.fileName) + ': ' + escapeHtml(r.error) + '</div>').join('');\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function buildSummaryHtml(allResults, sdkKeys, sdkLabels) {\n" +
                "    const hasAnyGT = allResults.some(r => r.sdkResults.some(s => s.gtResult !== null));\n" +
                "    const aggMap = {};\n" +
                "    for (const key of sdkKeys) {\n" +
                "        aggMap[key] = { total: 0, time: 0, tp: 0, expected: 0, fp: 0, uniqueTexts: new Set() };\n" +
                "    }\n" +
                "    for (const img of allResults) {\n" +
                "        for (let i = 0; i < sdkKeys.length; i++) {\n" +
                "            const key = sdkKeys[i];\n" +
                "            const r = img.sdkResults[i];\n" +
                "            aggMap[key].total += r.barcodes.length;\n" +
                "            aggMap[key].time += r.time;\n" +
                "            r.barcodes.forEach(b => { if (b.text) aggMap[key].uniqueTexts.add(b.text.trim()); });\n" +
                "            if (r.gtResult) { aggMap[key].tp += r.gtResult.tp; aggMap[key].expected += r.gtResult.total; aggMap[key].fp += r.gtResult.fp; }\n" +
                "        }\n" +
                "    }\n" +
                "    let html = '<div class=\"benchmark-summary\">';\n" +
                "    html += '<h4>Aggregate Summary (' + allResults.length + ' file(s))</h4>';\n" +
                "    html += '<div class=\"benchmark-table-wrap\"><table class=\"benchmark-table\"><thead><tr><th>SDK</th><th>Total Found</th><th>Unique Barcodes</th>';\n" +
                "    if (hasAnyGT) html += '<th>GT Expected</th><th>GT Detected</th><th>Detection Rate</th><th>Precision</th>';\n" +
                "    if (showBenchmarkTime) html += '<th>Total Time</th><th>Avg Time/Image</th>';\n" +
                "    html += '</tr></thead><tbody>';\n" +
                "    const maxTotal = Math.max(...sdkKeys.map(k => aggMap[k].total));\n" +
                "    for (const key of sdkKeys) {\n" +
                "        const a = aggMap[key];\n" +
                "        const isBest = a.total === maxTotal && maxTotal > 0;\n" +
                "        const detRate = a.expected > 0 ? a.tp / a.expected : null;\n" +
                "        const prec = (a.tp + a.fp) > 0 ? a.tp / (a.tp + a.fp) : null;\n" +
                "        html += '<tr><td class=\"sdk-col\">' + escapeHtml(sdkLabels[key]) + '</td>';\n" +
                "        html += '<td class=\"count-col' + (isBest ? ' best-count' : '') + '\">' + a.total + '</td>';\n" +
                "        html += '<td class=\"count-col\">' + a.uniqueTexts.size + '</td>';\n" +
                "        if (hasAnyGT) {\n" +
                "            html += '<td class=\"count-col\">' + a.expected + '</td>';\n" +
                "            html += '<td class=\"count-col\">' + a.tp + '</td>';\n" +
                "            html += '<td class=\"rate-col\">' + (detRate !== null ? '<span class=\"' + gtRateClass(detRate) + '\">' + (detRate * 100).toFixed(1) + '%</span>' : '<em>N/A</em>') + '</td>';\n" +
                "            html += '<td class=\"rate-col\">' + (prec !== null ? '<span class=\"' + gtRateClass(prec) + '\">' + (prec * 100).toFixed(1) + '%</span>' : '<em>N/A</em>') + '</td>';\n" +
                "        }\n" +
                "        if (showBenchmarkTime) html += '<td class=\"time-col\">' + a.time.toFixed(0) + ' ms</td><td class=\"time-col\">' + (allResults.length > 0 ? (a.time / allResults.length).toFixed(0) : 0) + ' ms</td>';\n" +
                "        html += '</tr>';\n" +
                "    }\n" +
                "    html += '</tbody></table></div>';\n" +
                "    const allUnique = new Set();\n" +
                "    sdkKeys.forEach(k => aggMap[k].uniqueTexts.forEach(t => allUnique.add(t)));\n" +
                "    const maxUnique = Math.max(...sdkKeys.map(k => aggMap[k].uniqueTexts.size));\n" +
                "    const mostBarcodes = sdkKeys.filter(k => aggMap[k].total === maxTotal && maxTotal > 0);\n" +
                "    const mostUnique = sdkKeys.filter(k => aggMap[k].uniqueTexts.size === maxUnique && maxUnique > 0);\n" +
                "    let bulletsHtml = '<ul>';\n" +
                "    bulletsHtml += '<li><strong>' + allUnique.size + '</strong> unique barcode(s) found across all SDKs and images</li>';\n" +
                "    if (mostBarcodes.length > 0) bulletsHtml += '<li>Most barcodes: <strong>' + mostBarcodes.map(k => escapeHtml(sdkLabels[k])).join(', ') + '</strong> (' + maxTotal + ')</li>';\n" +
                "    if (mostUnique.length > 0) bulletsHtml += '<li>Most unique barcodes: <strong>' + mostUnique.map(k => escapeHtml(sdkLabels[k])).join(', ') + '</strong> (' + maxUnique + ')</li>';\n" +
                "    if (hasAnyGT) {\n" +
                "        const maxRate = Math.max(...sdkKeys.map(k => aggMap[k].expected > 0 ? aggMap[k].tp / aggMap[k].expected : 0));\n" +
                "        const bestRateSDKs = sdkKeys.filter(k => aggMap[k].expected > 0 && Math.abs(aggMap[k].tp / aggMap[k].expected - maxRate) < 0.0001 && maxRate > 0);\n" +
                "        if (bestRateSDKs.length > 0) bulletsHtml += '<li>Best detection rate: <strong>' + bestRateSDKs.map(k => escapeHtml(sdkLabels[k])).join(', ') + '</strong> (' + (maxRate * 100).toFixed(1) + '%)</li>';\n" +
                "    }\n" +
                "    if (showBenchmarkTime) {\n" +
                "        const timesWithData = sdkKeys.filter(k => aggMap[k].time > 0);\n" +
                "        if (timesWithData.length > 0) {\n" +
                "            const minTime = Math.min(...timesWithData.map(k => aggMap[k].time));\n" +
                "            const fastestSDKs = timesWithData.filter(k => aggMap[k].time === minTime);\n" +
                "            bulletsHtml += '<li>Fastest: <strong>' + fastestSDKs.map(k => escapeHtml(sdkLabels[k])).join(', ') + '</strong> (' + minTime.toFixed(0) + ' ms total)</li>';\n" +
                "        }\n" +
                "    }\n" +
                "    bulletsHtml += '</ul>';\n" +
                "    html += bulletsHtml;\n" +
                "    html += '</div>';\n" +
                "    return html;\n" +
                "}\n" +
                "\n" +
                "function buildImageTableHtml(imgResult) {\n" +
                "    const hasGT = imgResult.sdkResults.some(r => r.gtResult !== null);\n" +
                "    const maxCount = Math.max(...imgResult.sdkResults.map(r => r.barcodes.length));\n" +
                "    let html = '<h4 class=\"benchmark-image-title\">' + escapeHtml(imgResult.imageName) + '</h4>';\n" +
                "    html += '<div class=\"benchmark-table-wrap\"><table class=\"benchmark-table\"><thead><tr><th>SDK</th><th>Found</th>';\n" +
                "    if (hasGT) html += '<th>Expected</th><th>Detected &#10003;</th><th>Rate</th><th>Precision</th>';\n" +
                "    if (showBenchmarkTime) html += '<th>Time</th>';\n" +
                "    html += '<th>Details</th></tr></thead><tbody>';\n" +
                "    for (const r of imgResult.sdkResults) {\n" +
                "        const count = r.barcodes.length;\n" +
                "        const isBest = count === maxCount && count > 0;\n" +
                "        let detailHtml = '';\n" +
                "        if (r.error) {\n" +
                "            detailHtml = '<em style=\"color:#ef4444;\">Error: ' + escapeHtml(r.error) + '</em>';\n" +
                "        } else if (count > 0) {\n" +
                "            detailHtml = '<ul class=\"barcodes-list\">' + r.barcodes.map(b => '<li>[' + escapeHtml(b.format) + '] ' + escapeHtml(b.text || '') + (b.frame ? ' (frame ' + b.frame + ')' : '') + '</li>').join('') + '</ul>';\n" +
                "        } else {\n" +
                "            detailHtml = '<em>Nothing found</em>';\n" +
                "        }\n" +
                "        html += '<tr><td class=\"sdk-col\">' + escapeHtml(r.sdkLabel) + '</td>';\n" +
                "        html += '<td class=\"count-col' + (isBest ? ' best-count' : '') + '\">' + count + '</td>';\n" +
                "        if (hasGT) {\n" +
                "            const gt = r.gtResult;\n" +
                "            html += '<td class=\"count-col\">' + (gt ? gt.total : '-') + '</td>';\n" +
                "            html += '<td class=\"count-col\">' + (gt ? gt.tp : '-') + '</td>';\n" +
                "            html += '<td class=\"rate-col\">' + (gt ? '<span class=\"' + gtRateClass(gt.detectionRate) + '\">' + (gt.detectionRate * 100).toFixed(1) + '%</span>' : '<em>N/A</em>') + '</td>';\n" +
                "            html += '<td class=\"rate-col\">' + (gt ? '<span class=\"' + gtRateClass(gt.precision) + '\">' + (gt.precision * 100).toFixed(1) + '%</span>' : '<em>N/A</em>') + '</td>';\n" +
                "        }\n" +
                "        if (showBenchmarkTime) html += '<td class=\"time-col\">' + r.time.toFixed(0) + ' ms</td>';\n" +
                "        html += '<td>' + detailHtml + '</td></tr>';\n" +
                "    }\n" +
                "    html += '</tbody></table></div>';\n" +
                "    return html;\n" +
                "}\n" +
                "\n" +
                "// ===== Export Report =====\n" +
                "function exportReport() {\n" +
                "    if (benchmarkResults.length === 0) return;\n" +
                "    const timestamp = new Date().toLocaleString();\n" +
                "    const summaryHtml = batchSummary.innerHTML;\n" +
                "    const detailsHtml = batchResults.innerHTML;\n" +
                "    const fullPage = `<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n" +
                "<title>Barcode Benchmark Report</title>\n" +
                "<style>\n" +
                "*,*::before,*::after{box-sizing:border-box}\n" +
                "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f8fafc;color:#1e293b;margin:0;padding:24px}\n" +
                ".report-header{background:#fff;border-radius:12px;padding:24px 28px;margin-bottom:24px;box-shadow:0 1px 4px rgba(0,0,0,.08)}\n" +
                ".report-header h1{margin:0 0 6px;font-size:1.6rem;color:#0f172a}\n" +
                ".report-header p{margin:2px 0;font-size:0.88rem;color:#64748b}\n" +
                ".benchmark-table-wrap{overflow-x:auto;margin-bottom:16px}\n" +
                ".benchmark-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.07);font-size:0.88rem}\n" +
                ".benchmark-table th{background:#f1f5f9;color:#475569;font-weight:600;text-align:left;padding:10px 14px;border-bottom:1px solid #e2e8f0}\n" +
                ".benchmark-table td{padding:9px 14px;border-bottom:1px solid #f1f5f9;vertical-align:top}\n" +
                ".benchmark-table tr:last-child td{border-bottom:none}\n" +
                ".sdk-col{font-weight:600;white-space:nowrap}\n" +
                ".count-col{text-align:center;font-weight:700;font-size:1.05em}\n" +
                ".time-col{text-align:right;color:#64748b;white-space:nowrap}\n" +
                ".rate-col{text-align:center}\n" +
                ".best-count{color:#16a34a}\n" +
                ".barcodes-list{list-style:none;padding:0;margin:0}\n" +
                ".barcodes-list li{font-size:0.82rem;color:#334155;padding:2px 0}\n" +
                ".gt-good{color:#16a34a;font-weight:700}\n" +
                ".gt-ok{color:#ca8a04;font-weight:700}\n" +
                ".gt-bad{color:#dc2626;font-weight:700}\n" +
                ".benchmark-image-title{color:#1e293b;font-size:0.93rem;font-weight:700;margin:18px 0 6px}\n" +
                ".benchmark-summary{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:16px 18px;margin-bottom:20px}\n" +
                ".benchmark-summary h4{color:#1e293b;font-size:0.95rem;margin-bottom:10px}\n" +
                ".benchmark-summary ul{list-style:none;padding:0;margin:10px 0 0}\n" +
                ".benchmark-summary ul li{font-size:0.88rem;color:#475569;padding:2px 0}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class=\\\"report-header\\\"><h1>Barcode Benchmark Report</h1><p>Generated: ${timestamp}</p><p>Files: ${benchmarkResults.length}</p></div>\n" +
                "${summaryHtml}${detailsHtml}\n" +
                "</body></html>`;\n" +
                "    const blob = new Blob([fullPage], { type: 'text/html' });\n" +
                "    const a = document.createElement('a');\n" +
                "    a.href = URL.createObjectURL(blob);\n" +
                "    a.download = 'benchmark_report_' + Date.now() + '.html';\n" +
                "    a.click();\n" +
                "}";
    }
}
