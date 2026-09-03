package com.dynamsoft.ionic.idscanner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumColourChannelUsageType;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.core.intermediate_results.ScaledColourImageUnit;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultManager;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.QuadDrawingItem;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.ddn.DetectedQuadResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.ddn.intermediate_results.DeskewedImageUnit;
import com.dynamsoft.ddn.intermediate_results.DetectedQuadsUnit;
import com.dynamsoft.diu.IdentityProcessor;
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLinesUnit;
import com.dynamsoft.dlr.intermediate_results.RecognizedTextLinesUnit;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen native camera scanner for identity documents.
 *
 * Live preview and processing are provided by the Dynamsoft MRZ Scanner native SDK
 * (CameraX + Capture Vision). When the MRZ zone and the portrait are detected the
 * capture button becomes enabled; tapping it returns the parsed fields and the
 * cropped portrait photo to the web layer.
 */
public class IdScanActivity extends AppCompatActivity {

    private static final String TAG = "IdScanActivity";

    private CameraEnhancer camera;
    private CameraView cameraView;
    private CaptureVisionRouter router;
    private IntermediateResultManager irm;
    private IntermediateResultReceiver intermediateReceiver;
    private CapturedResultReceiver resultReceiver;
    private final IdentityProcessor idProcessor = new IdentityProcessor();

    private TextView statusView;
    private Button captureButton;

    private ScaledColourImageUnit scaledColourImageUnit;
    private LocalizedTextLinesUnit localizedTextLinesUnit;
    private RecognizedTextLinesUnit recognizedTextLinesUnit;
    private DetectedQuadsUnit detectedQuadsUnit;
    private DeskewedImageUnit deskewedImageUnit;

    private Map<String, String> pendingFields;
    private Bitmap pendingPortrait;
    private Bitmap pendingDocument;

    private boolean confirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id_scan);

        cameraView = findViewById(R.id.camera_view);
        statusView = findViewById(R.id.status_text);
        captureButton = findViewById(R.id.btn_capture);
        Button cancelButton = findViewById(R.id.btn_cancel);

        cancelButton.setOnClickListener(v -> finishCanceled());
        captureButton.setOnClickListener(v -> confirm());

        IdScannerEngine engine = IdScannerEngine.getInstance();
        if (!engine.isLicenseInitialized() && !engine.initLicense(getString(R.string.dynamsoft_license))) {
            setResult(RESULT_CANCELED, ScanResultContract.error("Dynamsoft license initialization failed. Check your license key."));
            finish();
            return;
        }
        try {
            engine.ensureTemplates();
        } catch (IdScannerEngine.ScannerException e) {
            Log.e(TAG, "Template loading failed", e);
            setResult(RESULT_CANCELED, ScanResultContract.error(e.getMessage()));
            finish();
            return;
        }

        PermissionUtil.requestCameraPermission(this);
        router = engine.getRouter();
        camera = new CameraEnhancer(cameraView, this);
        camera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL);
        try {
            camera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER);
        } catch (CameraEnhancerException ignore) {
            // Frame filter is an optional quality improvement.
        }

        try {
            router.setInput(camera);
        } catch (CaptureVisionRouterException e) {
            Log.e(TAG, "Failed to bind camera to Capture Vision", e);
            setResult(RESULT_CANCELED, ScanResultContract.error("Failed to bind camera: " + e.getMessage()));
            finish();
            return;
        }

        intermediateReceiver = new IntermediateResultReceiver() {
            @Override
            public void onDetectedQuadsReceived(@NonNull DetectedQuadsUnit u, IntermediateResultExtraInfo i) {
                detectedQuadsUnit = u;
            }

            @Override
            public void onLocalizedTextLinesReceived(@NonNull LocalizedTextLinesUnit u, IntermediateResultExtraInfo i) {
                localizedTextLinesUnit = u;
            }

            @Override
            public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesUnit u, IntermediateResultExtraInfo i) {
                recognizedTextLinesUnit = u;
            }

            @Override
            public void onDeskewedImageReceived(@NonNull DeskewedImageUnit u, IntermediateResultExtraInfo i) {
                deskewedImageUnit = u;
            }

            @Override
            public void onScaledColourImageUnitReceived(@NonNull ScaledColourImageUnit u, IntermediateResultExtraInfo i) {
                scaledColourImageUnit = u;
            }
        };

        resultReceiver = new CapturedResultReceiver() {
            @Override
            public void onCapturedResultReceived(@NonNull com.dynamsoft.cvr.CapturedResult result) {
                ParsedResult pr = result.getParsedResult();
                if (pr == null || pr.getItems() == null || pr.getItems().length == 0) {
                    return;
                }
                ParsedResultItem item = pr.getItems()[0];
                Map<String, String> fields = IdResultFormatter.toFieldMap(item);
                if (fields.isEmpty()) {
                    return;
                }

                // Locate the portrait photo from the auxiliary region of the MRZ zone.
                Quadrilateral portraitZone = null;
                if (localizedTextLinesUnit != null
                        && localizedTextLinesUnit.getAuxiliaryRegionElementsCount() > 0) {
                    boolean highConfidence = false;
                    for (int i = 0; i < localizedTextLinesUnit.getAuxiliaryRegionElementsCount(); i++) {
                        if ("PortraitZone".equals(localizedTextLinesUnit.getAuxiliaryRegionElement(i).getName())) {
                            if (localizedTextLinesUnit.getAuxiliaryRegionElement(i).getConfidence() > 60) {
                                highConfidence = true;
                                break;
                            }
                        }
                    }
                    if (highConfidence && detectedQuadsUnit != null && detectedQuadsUnit.getCount() > 0) {
                        portraitZone = idProcessor.findPortraitZone(
                                scaledColourImageUnit,
                                localizedTextLinesUnit,
                                recognizedTextLinesUnit,
                                detectedQuadsUnit,
                                deskewedImageUnit);
                    }
                }

                // Keep the zone only when it sits inside the detected document.
                ProcessedDocumentResult documentResult = result.getProcessedDocumentResult();
                if (portraitZone != null && documentResult != null
                        && documentResult.getDetectedQuadResultItems() != null
                        && documentResult.getDetectedQuadResultItems().length > 0) {
                    Quadrilateral docRegion = documentResult.getDetectedQuadResultItems()[0].getLocation();
                    boolean valid = inside(docRegion, portraitZone) && docRegion.getArea() / portraitZone.getArea() >= 3;
                    if (!valid) {
                        portraitZone = null;
                    }
                }

                Bitmap portrait = null;
                if (portraitZone != null && scaledColourImageUnit != null) {
                    try {
                        Bitmap scaled = scaledColourImageUnit.getImageData().toBitmap();
                        if (scaled != null) {
                            portrait = deskewPortrait(scaled, portraitZone);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Portrait crop failed", e);
                    }
                }

                // The document-detection step of ReadPassportAndId also emits a
                // deskewed page image (the same pipeline the standalone document
                // scanner uses), so we surface it as a preview of the detected
                // card boundary + perspective correction.
                Bitmap documentImage = null;
                if (documentResult != null && documentResult.getDeskewedImageResultItems() != null
                        && documentResult.getDeskewedImageResultItems().length > 0) {
                    try {
                        com.dynamsoft.ddn.DeskewedImageResultItem deskewedItem =
                                documentResult.getDeskewedImageResultItems()[0];
                        if (deskewedItem.getImageData() != null) {
                            documentImage = deskewedItem.getImageData().toBitmap();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Deskewed document extraction failed", e);
                    }
                }

                final Quadrilateral zone = portraitZone;
                final Bitmap portraitImage = portrait;
                final Bitmap documentImageFinal = documentImage;
                final Map<String, String> fieldsResult = fields;
                runOnUiThread(() -> {
                    pendingFields = fieldsResult;
                    pendingPortrait = portraitImage;
                    pendingDocument = documentImageFinal;
                    captureButton.setEnabled(true);
                    statusView.setText(zone != null ? "Portrait found - ready" : "MRZ ready");
                    if (zone != null) {
                        drawFaceOverlay(zone);
                    }
                });
            }
        };

        irm = router.getIntermediateResultManager();
        irm.addResultReceiver(intermediateReceiver);
        router.addResultReceiver(resultReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pendingFields = null;
        pendingPortrait = null;
        pendingDocument = null;
        captureButton.setEnabled(false);
        statusView.setText(R.string.status_initializing);
        camera.open();
        router.startCapturing(IdScannerEngine.template(), new CompletionListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> statusView.setText(R.string.status_scanning));
            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> statusView.setText(errorString));
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        router.stopCapturing();
        camera.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (irm != null && intermediateReceiver != null) {
            irm.removeResultReceiver(intermediateReceiver);
        }
        if (router != null && resultReceiver != null) {
            router.removeResultReceiver(resultReceiver);
        }
    }

    @Override
    public void onBackPressed() {
        finishCanceled();
        super.onBackPressed();
    }

    private void drawFaceOverlay(Quadrilateral quad) {
        DrawingLayer layer = cameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID);
        if (layer == null) {
            return;
        }
        java.util.ArrayList<com.dynamsoft.dce.DrawingItem> items = new java.util.ArrayList<>();
        items.add(new QuadDrawingItem(quad));
        layer.setDrawingItems(items);
    }

    private void confirm() {
        if (confirmed || pendingFields == null) {
            return;
        }
        confirmed = true;
        router.stopCapturing();
        try {
            JSONObject json = new JSONObject();
            json.put("fields", new JSONObject(pendingFields));
            if (pendingPortrait != null) {
                json.put("portraitBase64", toBase64(pendingPortrait));
            }
            if (pendingDocument != null) {
                json.put("documentImageBase64", toBase64(pendingDocument));
            }
            setResult(RESULT_OK, ScanResultContract.ok(json.toString()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize result", e);
            setResult(RESULT_CANCELED, ScanResultContract.error("Failed to serialize result: " + e.getMessage()));
        }
        finish();
    }

    private void finishCanceled() {
        if (confirmed) {
            return;
        }
        setResult(RESULT_CANCELED, ScanResultContract.error("canceled"));
        finish();
    }

    private static String toBase64(Bitmap bitmap) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
        byte[] bytes = os.toByteArray();
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static boolean inside(Quadrilateral outer, Quadrilateral inner) {
        for (Point p : inner.points) {
            if (!outer.isPointInQuadrilateral(p)) {
                return false;
            }
        }
        return true;
    }

    private static Bitmap deskewPortrait(Bitmap src, Quadrilateral quad) {
        if (src == null || quad == null || quad.points == null || quad.points.length < 4) {
            return null;
        }
        Point[] pts = quad.points;
        float[] srcPts = {
                pts[0].x, pts[0].y,
                pts[1].x, pts[1].y,
                pts[2].x, pts[2].y,
                pts[3].x, pts[3].y
        };
        float w = Math.max(
                (float) Math.hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y),
                (float) Math.hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y));
        float h = Math.max(
                (float) Math.hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y),
                (float) Math.hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y));
        if (w <= 0 || h <= 0) {
            return null;
        }
        int iw = Math.round(w);
        int ih = Math.round(h);
        float[] dstPts = {0, 0, iw, 0, iw, ih, 0, ih};
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            return null;
        }
        Bitmap result = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(src, matrix, null);
        return result;
    }
}
