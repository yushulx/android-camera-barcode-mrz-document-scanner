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
                "                    <span class=\"radio-btn\">📷 Image</span>\n" +
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
                "                    <p>Drag & drop your file here</p>\n" +
                "                    <p class=\"or\">or</p>\n" +
                "                    <button class=\"browse-btn\" id=\"browseBtn\">Browse Files</button>\n" +
                "                </div>\n" +
                "                <input type=\"file\" id=\"fileInput\" accept=\"image/*,video/*\" hidden>\n" +
                "            </div>\n" +
                "\n" +
                "            <div class=\"file-preview\" id=\"filePreview\" style=\"display: none;\">\n" +
                "                <div class=\"preview-content\">\n" +
                "                    <img id=\"previewImage\" style=\"display: none;\">\n" +
                "                    <video id=\"previewVideo\" controls style=\"display: none;\"></video>\n" +
                "                </div>\n" +
                "                <div class=\"file-info\">\n" +
                "                    <span id=\"fileName\"></span>\n" +
                "                    <button class=\"remove-btn\" id=\"removeBtn\">✕</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "\n" +
                "            <button class=\"benchmark-btn\" id=\"benchmarkBtn\" disabled>Run Benchmark</button>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"loading\" id=\"loading\" style=\"display: none;\">\n" +
                "            <div class=\"spinner\"></div>\n" +
                "            <p>Processing... Please wait</p>\n" +
                "        </div>\n" +
                "\n" +
                "        <div class=\"results\" id=\"results\" style=\"display: none;\">\n" +
                "            <h2>Benchmark Results</h2>\n" +
                "            <div class=\"result-summary\" id=\"resultSummary\"></div>\n" +
                "\n" +
                "            <div class=\"comparison\">\n" +
                "                <div class=\"sdk-result dynamsoft\">\n" +
                "                    <div class=\"sdk-header\">\n" +
                "                        <span class=\"sdk-icon\">🔵</span>\n" +
                "                        <h3>Dynamsoft</h3>\n" +
                "                    </div>\n" +
                "                    <div class=\"sdk-stats\" id=\"dynamsoftStats\"></div>\n" +
                "                    <div class=\"barcode-list\" id=\"dynamsoftBarcodes\"></div>\n" +
                "                </div>\n" +
                "\n" +
                "                <div class=\"sdk-result mlkit\">\n" +
                "                    <div class=\"sdk-header\">\n" +
                "                        <span class=\"sdk-icon\">🟢</span>\n" +
                "                        <h3>Google MLkit</h3>\n" +
                "                    </div>\n" +
                "                    <div class=\"sdk-stats\" id=\"mlkitStats\"></div>\n" +
                "                    <div class=\"barcode-list\" id=\"mlkitBarcodes\"></div>\n" +
                "                </div>\n" +
                "            </div>\n" +
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
                "    max-width: 900px;\n" +
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
                ".file-type-selector label {\n" +
                "    cursor: pointer;\n" +
                "}\n" +
                "\n" +
                ".file-type-selector input {\n" +
                "    display: none;\n" +
                "}\n" +
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
                ".drop-icon {\n" +
                "    font-size: 3rem;\n" +
                "    display: block;\n" +
                "    margin-bottom: 15px;\n" +
                "}\n" +
                "\n" +
                ".drop-zone p {\n" +
                "    color: #888;\n" +
                "    margin-bottom: 10px;\n" +
                "}\n" +
                "\n" +
                ".or {\n" +
                "    color: #555;\n" +
                "    font-size: 0.9rem;\n" +
                "}\n" +
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
                ".browse-btn:hover {\n" +
                "    transform: scale(1.05);\n" +
                "}\n" +
                "\n" +
                ".file-preview {\n" +
                "    margin-top: 20px;\n" +
                "    background: rgba(0,0,0,0.3);\n" +
                "    border-radius: 10px;\n" +
                "    overflow: hidden;\n" +
                "}\n" +
                "\n" +
                ".preview-content img, .preview-content video {\n" +
                "    max-width: 100%;\n" +
                "    max-height: 300px;\n" +
                "    display: block;\n" +
                "    margin: 0 auto;\n" +
                "}\n" +
                "\n" +
                ".file-info {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    align-items: center;\n" +
                "    padding: 15px;\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "}\n" +
                "\n" +
                ".remove-btn {\n" +
                "    background: #ff4757;\n" +
                "    border: none;\n" +
                "    width: 30px;\n" +
                "    height: 30px;\n" +
                "    border-radius: 50%;\n" +
                "    color: white;\n" +
                "    cursor: pointer;\n" +
                "}\n" +
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
                ".benchmark-btn:disabled {\n" +
                "    opacity: 0.5;\n" +
                "    cursor: not-allowed;\n" +
                "}\n" +
                "\n" +
                ".benchmark-btn:not(:disabled):hover {\n" +
                "    transform: translateY(-2px);\n" +
                "    box-shadow: 0 10px 30px rgba(240,147,251,0.3);\n" +
                "}\n" +
                "\n" +
                ".loading {\n" +
                "    text-align: center;\n" +
                "    padding: 50px;\n" +
                "}\n" +
                "\n" +
                ".spinner {\n" +
                "    width: 50px;\n" +
                "    height: 50px;\n" +
                "    border: 4px solid rgba(255,255,255,0.2);\n" +
                "    border-top-color: #4facfe;\n" +
                "    border-radius: 50%;\n" +
                "    animation: spin 1s linear infinite;\n" +
                "    margin: 0 auto 20px;\n" +
                "}\n" +
                "\n" +
                "@keyframes spin {\n" +
                "    to { transform: rotate(360deg); }\n" +
                "}\n" +
                "\n" +
                ".results {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    border-radius: 20px;\n" +
                "    padding: 30px;\n" +
                "}\n" +
                "\n" +
                ".results h2 {\n" +
                "    text-align: center;\n" +
                "    margin-bottom: 25px;\n" +
                "}\n" +
                "\n" +
                ".result-summary {\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    padding: 20px;\n" +
                "    border-radius: 10px;\n" +
                "    margin-bottom: 25px;\n" +
                "    text-align: center;\n" +
                "}\n" +
                "\n" +
                ".comparison {\n" +
                "    display: grid;\n" +
                "    grid-template-columns: 1fr 1fr;\n" +
                "    gap: 20px;\n" +
                "}\n" +
                "\n" +
                "@media (max-width: 700px) {\n" +
                "    .comparison {\n" +
                "        grid-template-columns: 1fr;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                ".sdk-result {\n" +
                "    background: rgba(0,0,0,0.2);\n" +
                "    border-radius: 15px;\n" +
                "    padding: 20px;\n" +
                "}\n" +
                "\n" +
                ".sdk-result.dynamsoft {\n" +
                "    border-top: 3px solid #2196F3;\n" +
                "}\n" +
                "\n" +
                ".sdk-result.mlkit {\n" +
                "    border-top: 3px solid #4CAF50;\n" +
                "}\n" +
                "\n" +
                ".sdk-header {\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "    gap: 10px;\n" +
                "    margin-bottom: 15px;\n" +
                "}\n" +
                "\n" +
                ".sdk-icon {\n" +
                "    font-size: 1.5rem;\n" +
                "}\n" +
                "\n" +
                ".sdk-stats {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    padding: 15px;\n" +
                "    border-radius: 10px;\n" +
                "    margin-bottom: 15px;\n" +
                "}\n" +
                "\n" +
                ".stat-item {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    margin-bottom: 8px;\n" +
                "}\n" +
                "\n" +
                ".stat-label {\n" +
                "    color: #888;\n" +
                "}\n" +
                "\n" +
                ".stat-value {\n" +
                "    font-weight: 600;\n" +
                "}\n" +
                "\n" +
                ".barcode-list {\n" +
                "    max-height: 300px;\n" +
                "    overflow-y: auto;\n" +
                "}\n" +
                "\n" +
                ".barcode-item {\n" +
                "    background: rgba(255,255,255,0.05);\n" +
                "    padding: 12px;\n" +
                "    border-radius: 8px;\n" +
                "    margin-bottom: 8px;\n" +
                "}\n" +
                "\n" +
                ".barcode-format {\n" +
                "    font-size: 0.8rem;\n" +
                "    color: #4facfe;\n" +
                "    margin-bottom: 5px;\n" +
                "}\n" +
                "\n" +
                ".barcode-text {\n" +
                "    font-family: monospace;\n" +
                "    font-size: 0.9rem;\n" +
                "    word-break: break-all;\n" +
                "}\n" +
                "\n" +
                "footer {\n" +
                "    text-align: center;\n" +
                "    margin-top: 40px;\n" +
                "    color: #555;\n" +
                "    font-size: 0.9rem;\n" +
                "}";
    }

    private String getAppJs() {
        return "const dropZone = document.getElementById('dropZone');\n" +
                "const fileInput = document.getElementById('fileInput');\n" +
                "const browseBtn = document.getElementById('browseBtn');\n" +
                "const filePreview = document.getElementById('filePreview');\n" +
                "const previewImage = document.getElementById('previewImage');\n" +
                "const previewVideo = document.getElementById('previewVideo');\n" +
                "const fileName = document.getElementById('fileName');\n" +
                "const removeBtn = document.getElementById('removeBtn');\n" +
                "const benchmarkBtn = document.getElementById('benchmarkBtn');\n" +
                "const loading = document.getElementById('loading');\n" +
                "const results = document.getElementById('results');\n" +
                "\n" +
                "let selectedFile = null;\n" +
                "\n" +
                "// File type selection\n" +
                "document.querySelectorAll('input[name=\"fileType\"]').forEach(radio => {\n" +
                "    radio.addEventListener('change', (e) => {\n" +
                "        const isVideo = e.target.value === 'video';\n" +
                "        fileInput.accept = isVideo ? 'video/*' : 'image/*';\n" +
                "        resetPreview();\n" +
                "    });\n" +
                "});\n" +
                "\n" +
                "// Drag and drop\n" +
                "dropZone.addEventListener('dragover', (e) => {\n" +
                "    e.preventDefault();\n" +
                "    dropZone.classList.add('dragover');\n" +
                "});\n" +
                "\n" +
                "dropZone.addEventListener('dragleave', () => {\n" +
                "    dropZone.classList.remove('dragover');\n" +
                "});\n" +
                "\n" +
                "dropZone.addEventListener('drop', (e) => {\n" +
                "    e.preventDefault();\n" +
                "    dropZone.classList.remove('dragover');\n" +
                "    const files = e.dataTransfer.files;\n" +
                "    if (files.length) handleFile(files[0]);\n" +
                "});\n" +
                "\n" +
                "dropZone.addEventListener('click', () => fileInput.click());\n" +
                "browseBtn.addEventListener('click', (e) => {\n" +
                "    e.stopPropagation();\n" +
                "    fileInput.click();\n" +
                "});\n" +
                "\n" +
                "fileInput.addEventListener('change', (e) => {\n" +
                "    if (e.target.files.length) handleFile(e.target.files[0]);\n" +
                "});\n" +
                "\n" +
                "removeBtn.addEventListener('click', resetPreview);\n" +
                "\n" +
                "function handleFile(file) {\n" +
                "    selectedFile = file;\n" +
                "    fileName.textContent = file.name;\n" +
                "    \n" +
                "    const isVideo = file.type.startsWith('video/');\n" +
                "    const url = URL.createObjectURL(file);\n" +
                "    \n" +
                "    if (isVideo) {\n" +
                "        previewVideo.src = url;\n" +
                "        previewVideo.style.display = 'block';\n" +
                "        previewImage.style.display = 'none';\n" +
                "        document.querySelector('input[value=\"video\"]').checked = true;\n" +
                "    } else {\n" +
                "        previewImage.src = url;\n" +
                "        previewImage.style.display = 'block';\n" +
                "        previewVideo.style.display = 'none';\n" +
                "        document.querySelector('input[value=\"image\"]').checked = true;\n" +
                "    }\n" +
                "    \n" +
                "    dropZone.style.display = 'none';\n" +
                "    filePreview.style.display = 'block';\n" +
                "    benchmarkBtn.disabled = false;\n" +
                "    results.style.display = 'none';\n" +
                "}\n" +
                "\n" +
                "function resetPreview() {\n" +
                "    selectedFile = null;\n" +
                "    fileInput.value = '';\n" +
                "    previewImage.src = '';\n" +
                "    previewVideo.src = '';\n" +
                "    dropZone.style.display = 'block';\n" +
                "    filePreview.style.display = 'none';\n" +
                "    benchmarkBtn.disabled = true;\n" +
                "}\n" +
                "\n" +
                "benchmarkBtn.addEventListener('click', async () => {\n" +
                "    if (!selectedFile) return;\n" +
                "    \n" +
                "    loading.style.display = 'block';\n" +
                "    results.style.display = 'none';\n" +
                "    benchmarkBtn.disabled = true;\n" +
                "    \n" +
                "    const formData = new FormData();\n" +
                "    formData.append('file', selectedFile);\n" +
                "    formData.append('fileType', selectedFile.type.startsWith('video/') ? 'video' : 'image');\n" +
                "    \n" +
                "    try {\n" +
                "        const response = await fetch('/api/benchmark', {\n" +
                "            method: 'POST',\n" +
                "            body: formData\n" +
                "        });\n" +
                "        \n" +
                "        const data = await response.json();\n" +
                "        displayResults(data);\n" +
                "    } catch (error) {\n" +
                "        alert('Error: ' + error.message);\n" +
                "    } finally {\n" +
                "        loading.style.display = 'none';\n" +
                "        benchmarkBtn.disabled = false;\n" +
                "    }\n" +
                "});\n" +
                "\n" +
                "function displayResults(data) {\n" +
                "    results.style.display = 'block';\n" +
                "    \n" +
                "    // Summary\n" +
                "    const summary = document.getElementById('resultSummary');\n" +
                "    if (data.type === 'video') {\n" +
                "        summary.innerHTML = `<p>📹 Video: ${data.framesExtracted} frames extracted (${data.durationMs}ms duration)</p>`;\n" +
                "    } else {\n" +
                "        summary.innerHTML = `<p>🖼️ Image: ${data.width} × ${data.height} pixels</p>`;\n" +
                "    }\n" +
                "    \n" +
                "    // Dynamsoft stats\n" +
                "    const dynamsoftStats = document.getElementById('dynamsoftStats');\n" +
                "    dynamsoftStats.innerHTML = `\n" +
                "        <div class=\"stat-item\"><span class=\"stat-label\">Time:</span><span class=\"stat-value\">${data.dynamsoft.timeMs}ms</span></div>\n" +
                "        <div class=\"stat-item\"><span class=\"stat-label\">Barcodes:</span><span class=\"stat-value\">${data.dynamsoft.count}</span></div>\n" +
                "        ${data.dynamsoft.framesProcessed ? `<div class=\"stat-item\"><span class=\"stat-label\">Frames:</span><span class=\"stat-value\">${data.dynamsoft.framesProcessed}</span></div>` : ''}\n" +
                "    `;\n" +
                "    \n" +
                "    // MLkit stats\n" +
                "    const mlkitStats = document.getElementById('mlkitStats');\n" +
                "    mlkitStats.innerHTML = `\n" +
                "        <div class=\"stat-item\"><span class=\"stat-label\">Time:</span><span class=\"stat-value\">${data.mlkit.timeMs}ms</span></div>\n" +
                "        <div class=\"stat-item\"><span class=\"stat-label\">Barcodes:</span><span class=\"stat-value\">${data.mlkit.count}</span></div>\n" +
                "        ${data.mlkit.framesProcessed ? `<div class=\"stat-item\"><span class=\"stat-label\">Frames:</span><span class=\"stat-value\">${data.mlkit.framesProcessed}</span></div>` : ''}\n" +
                "    `;\n" +
                "    \n" +
                "    // Barcode lists\n" +
                "    document.getElementById('dynamsoftBarcodes').innerHTML = renderBarcodeList(data.dynamsoft.barcodes);\n" +
                "    document.getElementById('mlkitBarcodes').innerHTML = renderBarcodeList(data.mlkit.barcodes);\n" +
                "}\n" +
                "\n" +
                "function renderBarcodeList(barcodes) {\n" +
                "    if (!barcodes || barcodes.length === 0) {\n" +
                "        return '<p style=\"color: #888; text-align: center;\">No barcodes detected</p>';\n" +
                "    }\n" +
                "    return barcodes.map(bc => `\n" +
                "        <div class=\"barcode-item\">\n" +
                "            <div class=\"barcode-format\">${bc.format}${bc.frame ? ` (Frame ${bc.frame})` : ''}</div>\n" +
                "            <div class=\"barcode-text\">${bc.text || '(empty)'}</div>\n" +
                "        </div>\n" +
                "    `).join('');\n" +
                "}";
    }
}
