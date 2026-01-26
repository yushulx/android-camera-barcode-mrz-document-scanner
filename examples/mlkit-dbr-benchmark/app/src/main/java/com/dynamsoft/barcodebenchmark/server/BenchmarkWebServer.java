package com.dynamsoft.barcodebenchmark.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

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

public class BenchmarkWebServer extends NanoHTTPD {

    private static final String TAG = "BenchmarkWebServer";
    private final Context context;
    private CaptureVisionRouter cvRouter;
    private BarcodeScanner mlkitScanner;

    public BenchmarkWebServer(Context context, int port) {
        super(port);
        this.context = context;
        initializeScanners();
    }

    private void initializeScanners() {
        try {
            cvRouter = new CaptureVisionRouter(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Dynamsoft CVR", e);
        }

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        mlkitScanner = BarcodeScanning.getClient(options);
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
            } else if (uri.equals("/api/status")) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", 
                    "{\"status\":\"running\",\"dynamsoft\":" + (cvRouter != null) + ",\"mlkit\":" + (mlkitScanner != null) + "}");
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

        long startTime = System.currentTimeMillis();
        CapturedResult capturedResult = cvRouter.capture(bitmap, EnumPresetTemplate.PT_READ_BARCODES);
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
            
            long startTime = System.currentTimeMillis();
            CapturedResult capturedResult = cvRouter.capture(frame, EnumPresetTemplate.PT_READ_BARCODES);
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
                "            <h1>🔍 Barcode Benchmark</h1>\n" +
                "            <p>Compare Dynamsoft Barcode Reader vs Google MLkit</p>\n" +
                "        </header>\n" +
                "\n" +
                "        <div class=\"upload-section\">\n" +
                "            <div class=\"file-type-selector\">\n" +
                "                <label>\n" +
                "                    <input type=\"radio\" name=\"fileType\" value=\"image\" checked>\n" +
                "                    <span class=\"radio-btn\">📷 Images</span>\n" +
                "                </label>\n" +
                "                <label>\n" +
                "                    <input type=\"radio\" name=\"fileType\" value=\"video\">\n" +
                "                    <span class=\"radio-btn\">🎬 Video</span>\n" +
                "                </label>\n" +
                "            </div>\n" +
                "\n" +
                "            <div class=\"drop-zone\" id=\"dropZone\">\n" +
                "                <div class=\"drop-zone-content\">\n" +
                "                    <span class=\"drop-icon\">📁</span>\n" +
                "                    <p>Drag & drop files or folders here</p>\n" +
                "                    <p class=\"hint\">Supports multiple images or a folder</p>\n" +
                "                    <p class=\"or\">or</p>\n" +
                "                    <button class=\"browse-btn\" id=\"browseBtn\">Browse Files</button>\n" +
                "                </div>\n" +
                "                <input type=\"file\" id=\"fileInput\" accept=\"image/*,video/*\" multiple hidden>\n" +
                "            </div>\n" +
                "\n" +
                "            <div class=\"file-list\" id=\"fileList\" style=\"display: none;\">\n" +
                "                <div class=\"file-list-header\">\n" +
                "                    <span id=\"fileCount\">0 files selected</span>\n" +
                "                    <button class=\"clear-btn\" id=\"clearBtn\">Clear All</button>\n" +
                "                </div>\n" +
                "                <div class=\"file-items\" id=\"fileItems\"></div>\n" +
                "            </div>\n" +
                "\n" +
                "            <button class=\"benchmark-btn\" id=\"benchmarkBtn\" disabled>Run Benchmark</button>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"progress-section\" id=\"progressSection\" style=\"display: none;\">\n" +
                "            <div class=\"progress-header\">\n" +
                "                <span id=\"progressText\">Processing...</span>\n" +
                "                <span id=\"progressCount\">0/0</span>\n" +
                "            </div>\n" +
                "            <div class=\"progress-bar\">\n" +
                "                <div class=\"progress-fill\" id=\"progressFill\"></div>\n" +
                "            </div>\n" +
                "            <div class=\"current-file\" id=\"currentFile\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"results\" id=\"results\" style=\"display: none;\">\n" +
                "            <h2>Batch Benchmark Results</h2>\n" +
                "            <div class=\"batch-summary\" id=\"batchSummary\"></div>\n" +
                "            <div class=\"batch-results\" id=\"batchResults\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <footer>\n" +
                "            <p>Powered by Android • Dynamsoft Barcode Reader SDK v11.2</p>\n" +
                "        </footer>\n" +
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
                ".result-item.expanded .expand-icon { transform: rotate(180deg); }";
    }

    private String getAppJs() {
        return "const dropZone = document.getElementById('dropZone');\n" +
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
                "\n" +
                "// Drag and drop with folder support\n" +
                "dropZone.addEventListener('dragover', (e) => {\n" +
                "    e.preventDefault();\n" +
                "    dropZone.classList.add('dragover');\n" +
                "});\n" +
                "\n" +
                "dropZone.addEventListener('dragleave', () => {\n" +
                "    dropZone.classList.remove('dragover');\n" +
                "});\n" +
                "\n" +
                "dropZone.addEventListener('drop', async (e) => {\n" +
                "    e.preventDefault();\n" +
                "    dropZone.classList.remove('dragover');\n" +
                "    const items = e.dataTransfer.items;\n" +
                "    const files = [];\n" +
                "    \n" +
                "    // Process items for folder support\n" +
                "    const promises = [];\n" +
                "    for (let i = 0; i < items.length; i++) {\n" +
                "        const item = items[i];\n" +
                "        if (item.webkitGetAsEntry) {\n" +
                "            const entry = item.webkitGetAsEntry();\n" +
                "            if (entry) {\n" +
                "                promises.push(traverseEntry(entry));\n" +
                "            }\n" +
                "        } else if (item.getAsFile) {\n" +
                "            const file = item.getAsFile();\n" +
                "            if (file && isValidFile(file)) files.push(file);\n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    const nestedFiles = await Promise.all(promises);\n" +
                "    nestedFiles.flat().forEach(f => { if (isValidFile(f)) files.push(f); });\n" +
                "    \n" +
                "    addFiles(files);\n" +
                "});\n" +
                "\n" +
                "async function traverseEntry(entry) {\n" +
                "    if (entry.isFile) {\n" +
                "        return new Promise(resolve => {\n" +
                "            entry.file(file => resolve([file]), () => resolve([]));\n" +
                "        });\n" +
                "    } else if (entry.isDirectory) {\n" +
                "        const reader = entry.createReader();\n" +
                "        return new Promise(resolve => {\n" +
                "            reader.readEntries(async entries => {\n" +
                "                const files = [];\n" +
                "                for (const e of entries) {\n" +
                "                    const subFiles = await traverseEntry(e);\n" +
                "                    files.push(...subFiles);\n" +
                "                }\n" +
                "                resolve(files);\n" +
                "            }, () => resolve([]));\n" +
                "        });\n" +
                "    }\n" +
                "    return [];\n" +
                "}\n" +
                "\n" +
                "function isValidFile(file) {\n" +
                "    return file.type.startsWith('image/') || file.type.startsWith('video/');\n" +
                "}\n" +
                "\n" +
                "function formatSize(bytes) {\n" +
                "    if (bytes < 1024) return bytes + ' B';\n" +
                "    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';\n" +
                "    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';\n" +
                "}\n" +
                "\n" +
                "function addFiles(files) {\n" +
                "    files.forEach(file => {\n" +
                "        // Avoid duplicates\n" +
                "        if (!selectedFiles.find(f => f.name === file.name && f.size === file.size)) {\n" +
                "            selectedFiles.push(file);\n" +
                "        }\n" +
                "    });\n" +
                "    updateFileList();\n" +
                "}\n" +
                "\n" +
                "function updateFileList() {\n" +
                "    if (selectedFiles.length === 0) {\n" +
                "        fileList.style.display = 'none';\n" +
                "        benchmarkBtn.disabled = true;\n" +
                "        return;\n" +
                "    }\n" +
                "    \n" +
                "    fileList.style.display = 'block';\n" +
                "    benchmarkBtn.disabled = false;\n" +
                "    fileCount.textContent = selectedFiles.length + ' file(s) selected';\n" +
                "    \n" +
                "    fileItems.innerHTML = selectedFiles.map((file, idx) => `\n" +
                "        <div class=\"file-item\" data-idx=\"${idx}\">\n" +
                "            <span class=\"file-item-icon\">${file.type.startsWith('video/') ? '🎬' : '🖼️'}</span>\n" +
                "            <span class=\"file-item-name\">${file.name}</span>\n" +
                "            <span class=\"file-item-size\">${formatSize(file.size)}</span>\n" +
                "            <span class=\"file-item-status\" id=\"status-${idx}\"></span>\n" +
                "        </div>\n" +
                "    `).join('');\n" +
                "}\n" +
                "\n" +
                "dropZone.addEventListener('click', () => fileInput.click());\n" +
                "browseBtn.addEventListener('click', (e) => {\n" +
                "    e.stopPropagation();\n" +
                "    fileInput.click();\n" +
                "});\n" +
                "\n" +
                "fileInput.addEventListener('change', (e) => {\n" +
                "    addFiles(Array.from(e.target.files));\n" +
                "    fileInput.value = '';\n" +
                "});\n" +
                "\n" +
                "clearBtn.addEventListener('click', () => {\n" +
                "    selectedFiles = [];\n" +
                "    updateFileList();\n" +
                "    results.style.display = 'none';\n" +
                "    progressSection.style.display = 'none';\n" +
                "});\n" +
                "\n" +
                "benchmarkBtn.addEventListener('click', async () => {\n" +
                "    if (selectedFiles.length === 0) return;\n" +
                "    \n" +
                "    progressSection.style.display = 'block';\n" +
                "    results.style.display = 'none';\n" +
                "    benchmarkBtn.disabled = true;\n" +
                "    benchmarkResults = [];\n" +
                "    \n" +
                "    for (let i = 0; i < selectedFiles.length; i++) {\n" +
                "        const file = selectedFiles[i];\n" +
                "        const progress = Math.round(((i) / selectedFiles.length) * 100);\n" +
                "        progressFill.style.width = progress + '%';\n" +
                "        progressText.textContent = `${i + 1} / ${selectedFiles.length}`;\n" +
                "        currentFile.textContent = `Processing: ${file.name}`;\n" +
                "        \n" +
                "        // Update status icon\n" +
                "        const statusEl = document.getElementById('status-' + i);\n" +
                "        if (statusEl) statusEl.textContent = '⏳';\n" +
                "        \n" +
                "        try {\n" +
                "            const formData = new FormData();\n" +
                "            formData.append('file', file);\n" +
                "            formData.append('fileType', file.type.startsWith('video/') ? 'video' : 'image');\n" +
                "            \n" +
                "            const response = await fetch('/api/benchmark', {\n" +
                "                method: 'POST',\n" +
                "                body: formData\n" +
                "            });\n" +
                "            \n" +
                "            const data = await response.json();\n" +
                "            data.fileName = file.name;\n" +
                "            benchmarkResults.push(data);\n" +
                "            if (statusEl) statusEl.textContent = '✅';\n" +
                "        } catch (error) {\n" +
                "            benchmarkResults.push({ fileName: file.name, error: error.message });\n" +
                "            if (statusEl) statusEl.textContent = '❌';\n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    progressFill.style.width = '100%';\n" +
                "    progressText.textContent = 'Complete!';\n" +
                "    currentFile.textContent = '';\n" +
                "    benchmarkBtn.disabled = false;\n" +
                "    \n" +
                "    displayBatchResults();\n" +
                "});\n" +
                "\n" +
                "function displayBatchResults() {\n" +
                "    results.style.display = 'block';\n" +
                "    \n" +
                "    // Calculate totals\n" +
                "    let totalDynamsoftBarcodes = 0, totalMlkitBarcodes = 0;\n" +
                "    let totalDynamsoftTime = 0, totalMlkitTime = 0;\n" +
                "    let successCount = 0;\n" +
                "    \n" +
                "    benchmarkResults.forEach(r => {\n" +
                "        if (!r.error) {\n" +
                "            successCount++;\n" +
                "            totalDynamsoftBarcodes += r.dynamsoft?.count || 0;\n" +
                "            totalMlkitBarcodes += r.mlkit?.count || 0;\n" +
                "            totalDynamsoftTime += r.dynamsoft?.timeMs || 0;\n" +
                "            totalMlkitTime += r.mlkit?.timeMs || 0;\n" +
                "        }\n" +
                "    });\n" +
                "    \n" +
                "    batchSummary.innerHTML = `\n" +
                "        <div class=\"summary-card\"><div class=\"value\">${benchmarkResults.length}</div><div class=\"label\">Files Processed</div></div>\n" +
                "        <div class=\"summary-card dynamsoft\"><div class=\"value\">${totalDynamsoftBarcodes}</div><div class=\"label\">Dynamsoft Total</div></div>\n" +
                "        <div class=\"summary-card mlkit\"><div class=\"value\">${totalMlkitBarcodes}</div><div class=\"label\">MLkit Total</div></div>\n" +
                "        <div class=\"summary-card dynamsoft\"><div class=\"value\">${totalDynamsoftTime}ms</div><div class=\"label\">Dynamsoft Time</div></div>\n" +
                "        <div class=\"summary-card mlkit\"><div class=\"value\">${totalMlkitTime}ms</div><div class=\"label\">MLkit Time</div></div>\n" +
                "    `;\n" +
                "    \n" +
                "    batchResults.innerHTML = benchmarkResults.map((r, idx) => {\n" +
                "        if (r.error) {\n" +
                "            return `<div class=\"result-item\"><div class=\"result-item-header\"><span class=\"result-item-name\">❌ ${r.fileName}</span><span>Error: ${r.error}</span></div></div>`;\n" +
                "        }\n" +
                "        return `\n" +
                "            <div class=\"result-item\" id=\"result-${idx}\">\n" +
                "                <div class=\"result-item-header\" onclick=\"toggleResult(${idx})\">\n" +
                "                    <span class=\"result-item-name\">${r.type === 'video' ? '🎬' : '🖼️'} ${r.fileName}</span>\n" +
                "                    <div class=\"result-item-stats\">\n" +
                "                        <span class=\"dynamsoft\">DBR: ${r.dynamsoft?.count || 0} (${r.dynamsoft?.timeMs || 0}ms)</span>\n" +
                "                        <span class=\"mlkit\">MLkit: ${r.mlkit?.count || 0} (${r.mlkit?.timeMs || 0}ms)</span>\n" +
                "                        <span class=\"expand-icon\">▼</span>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "                <div class=\"result-item-details\">\n" +
                "                    <div class=\"comparison\">\n" +
                "                        <div class=\"sdk-result dynamsoft\">\n" +
                "                            <div class=\"sdk-header\"><span class=\"sdk-icon\">🔷</span><h4>Dynamsoft Barcode Reader</h4></div>\n" +
                "                            <div class=\"barcode-list\">${renderBarcodeList(r.dynamsoft?.barcodes)}</div>\n" +
                "                        </div>\n" +
                "                        <div class=\"sdk-result mlkit\">\n" +
                "                            <div class=\"sdk-header\"><span class=\"sdk-icon\">🟢</span><h4>Google MLkit</h4></div>\n" +
                "                            <div class=\"barcode-list\">${renderBarcodeList(r.mlkit?.barcodes)}</div>\n" +
                "                        </div>\n" +
                "                    </div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        `;\n" +
                "    }).join('');\n" +
                "}\n" +
                "\n" +
                "function toggleResult(idx) {\n" +
                "    const el = document.getElementById('result-' + idx);\n" +
                "    if (el) el.classList.toggle('expanded');\n" +
                "}\n" +
                "\n" +
                "function renderBarcodeList(barcodes) {\n" +
                "    if (!barcodes || barcodes.length === 0) {\n" +
                "        return '<p style=\"color: #888; text-align: center;\">No barcodes detected</p>';\n" +
                "    }\n" +
                "    return barcodes.map(bc => `\n" +
                "        <div class=\\\"barcode-item\\\">\n" +
                "            <div class=\\\"barcode-format\\\">${bc.format}${bc.frame ? ` (Frame ${bc.frame})` : ''}</div>\n" +
                "            <div class=\\\"barcode-text\\\">${bc.text || '(empty)'}</div>\n" +
                "        </div>\n" +
                "    `).join('');\n" +
                "}";
    }
}
