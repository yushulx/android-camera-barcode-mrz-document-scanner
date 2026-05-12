package com.test.mrzscanner;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.EnumColourChannelUsageType;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.core.intermediate_results.ScaledColourImageUnit;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.SimplifiedCaptureVisionSettings;
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
import com.dynamsoft.ddn.DetectedQuadResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.ddn.intermediate_results.DeskewedImageUnit;
import com.dynamsoft.ddn.intermediate_results.DetectedQuadsUnit;
import com.dynamsoft.diu.IdentityProcessor;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLinesUnit;
import com.dynamsoft.dlr.intermediate_results.RecognizedTextLinesUnit;
import com.dynamsoft.utility.CrossVerificationCriteria;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private CameraEnhancer mCamera;
    private CameraView mCameraView;
    private final CaptureVisionRouter mRouter = new CaptureVisionRouter();
    private IntermediateResultManager mIrm;
    private String mText = "";
    private AlertDialog mAlertDialog;
    private final IdentityProcessor mIdProcessor = new IdentityProcessor();

    private ScaledColourImageUnit mScaledColourImageUnit;
    private LocalizedTextLinesUnit mLocalizedTextLinesUnit;
    private RecognizedTextLinesUnit mRecognizedTextLinesUnit;
    private DetectedQuadsUnit mDetectedQuadsUnit;
    private DeskewedImageUnit mDeskewedImageUnit;

    private HashMap<String, String> mPendingLabelMap;
    private Bitmap mPendingPortraitBitmap;

    private ImageView btnBack;
    private TextView tvMessage, tvResult, tvStatus, tvInstruction;
    private CircularProgressIndicator progressIndicator;
    private MaterialButton btnCapture;

    private final IntermediateResultReceiver mIntermediateReceiver = new IntermediateResultReceiver() {
        @Override public void onDetectedQuadsReceived(@NonNull DetectedQuadsUnit u, IntermediateResultExtraInfo i) { mDetectedQuadsUnit = u; }
        @Override public void onLocalizedTextLinesReceived(@NonNull LocalizedTextLinesUnit u, IntermediateResultExtraInfo i) { mLocalizedTextLinesUnit = u; }
        @Override public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesUnit u, IntermediateResultExtraInfo i) { mRecognizedTextLinesUnit = u; }
        @Override public void onDeskewedImageReceived(@NonNull DeskewedImageUnit u, IntermediateResultExtraInfo i) { mDeskewedImageUnit = u; }
        @Override public void onScaledColourImageUnitReceived(@NonNull ScaledColourImageUnit u, IntermediateResultExtraInfo i) { mScaledColourImageUnit = u; }
    };

    private final CapturedResultReceiver mResultReceiver = new CapturedResultReceiver() {
        @Override
        public void onCapturedResultReceived(@NonNull CapturedResult result) {
            ParsedResult pr = result.getParsedResult();
            if (pr == null || pr.getItems().length == 0) return;
            HashMap<String, String> map = (HashMap<String, String>) MrzParser.parse(pr.getItems()[0]);
            if (map.isEmpty()) return;

            // Get document quad for validation (matching SDK's MRZScanner.java)
            DetectedQuadResultItem quadItem = null;
            ProcessedDocumentResult documentResult = result.getProcessedDocumentResult();
            if (documentResult != null && documentResult.getDetectedQuadResultItems().length > 0) {
                quadItem = documentResult.getDetectedQuadResultItems()[0];
            }

            // Check for high-confidence PortraitZone auxiliary region before running findPortraitZone
            Quadrilateral pz = null;
            if (mScaledColourImageUnit != null && mLocalizedTextLinesUnit != null) {
                int highConfIdx = -1;
                if (mLocalizedTextLinesUnit.getAuxiliaryRegionElementsCount() > 0) {
                    for (int i = 0; i < mLocalizedTextLinesUnit.getAuxiliaryRegionElementsCount(); i++) {
                        if ("PortraitZone".equals(mLocalizedTextLinesUnit.getAuxiliaryRegionElement(i).getName())) {
                            if (mLocalizedTextLinesUnit.getAuxiliaryRegionElement(i).getConfidence() > 60) {
                                highConfIdx = i;
                                break;
                            }
                        }
                    }
                }
                if (highConfIdx != -1 && mDetectedQuadsUnit != null && mDetectedQuadsUnit.getCount() > 0) {
                    pz = mIdProcessor.findPortraitZone(mScaledColourImageUnit, mLocalizedTextLinesUnit,
                            mRecognizedTextLinesUnit, mDetectedQuadsUnit, mDeskewedImageUnit);
                }
            }

            // Validate portrait is inside document and proportionally reasonable
            if (pz != null && quadItem != null) {
                Quadrilateral docRegion = quadItem.getLocation();
                boolean valid = docRegion.isPointInQuadrilateral(pz.points[0])
                        && docRegion.isPointInQuadrilateral(pz.points[1])
                        && docRegion.isPointInQuadrilateral(pz.points[2])
                        && docRegion.isPointInQuadrilateral(pz.points[3])
                        && docRegion.getArea() / pz.getArea() >= 3;
                if (!valid) pz = null;
            }

            final Quadrilateral portraitZone = pz;
            if (portraitZone != null) runOnUiThread(() -> drawFaceOverlay(portraitZone));

            // Crop portrait from ScaledColourImageUnit (coordinates match this image space)
            Bitmap portrait = null;
            if (portraitZone != null && mScaledColourImageUnit != null) {
                try {
                    Bitmap scaled = mScaledColourImageUnit.getImageData().toBitmap();
                    if (scaled != null) portrait = deskewPortrait(scaled, portraitZone);
                } catch (Exception e) {
                    Log.e(TAG, "Portrait crop failed", e);
                }
            }

            mPendingLabelMap = map;
            mPendingPortraitBitmap = portrait;
            final boolean ok = portrait != null;
            runOnUiThread(() -> {
                progressIndicator.setVisibility(View.GONE);
                btnCapture.setEnabled(true);
                updateStatus(ok ? "Ready" : "MRZ ready");
            });
        }

        @Override
        public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesResult r) {
            if (r.getItems() == null) return;
            TextLineResultItem[] items = r.getItems();
            if (items != null && items.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (TextLineResultItem it : items) sb.append(it.getText()).append("\n");
                mText = sb.toString();
                runOnUiThread(() -> { if (!mText.isEmpty()) { tvResult.setText(mText.trim()); tvResult.setVisibility(View.VISIBLE); } });
            }
        }
    };

    private void drawFaceOverlay(Quadrilateral q) {
        DrawingLayer layer = mCameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID);
        if (layer == null) return;
        java.util.ArrayList<com.dynamsoft.dce.DrawingItem> items = new java.util.ArrayList<>();
        items.add(new QuadDrawingItem(q));
        layer.addDrawingItems(items);
    }

    @Override protected void onCreate(Bundle ss) {
        super.onCreate(ss);
        setContentView(R.layout.activity_scan);
        mCameraView = findViewById(R.id.dce_camera_view);
        btnBack = findViewById(R.id.btn_back); btnBack.setOnClickListener(v -> finish());
        tvMessage = findViewById(R.id.tv_message); tvResult = findViewById(R.id.tv_result);
        tvStatus = findViewById(R.id.tv_status); tvInstruction = findViewById(R.id.tv_instruction);
        progressIndicator = findViewById(R.id.progress_indicator);
        btnCapture = findViewById(R.id.btn_capture); btnCapture.setOnClickListener(v -> onCapture());

        PermissionUtil.requestCameraPermission(this); MrzParser.initLicense();
        mCamera = new CameraEnhancer(mCameraView, this);
        mCamera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL);
        try { mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER); } catch (CameraEnhancerException ignore) {}

        MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
        filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_TEXT_LINE | EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE | EnumCapturedResultItemType.CRIT_DETECTED_QUAD, true);
        filter.setResultCrossVerificationCriteria(EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE | EnumCapturedResultItemType.CRIT_DETECTED_QUAD, new CrossVerificationCriteria(5, 2));
        mRouter.addResultFilter(filter);

        try {
            mRouter.initSettingsFromFile("mrzscanner-mobile-templates.json");
            mRouter.setInput(mCamera);
            SimplifiedCaptureVisionSettings st = mRouter.getSimplifiedSettings("ReadPassportAndId");
            if (st.documentSettings != null) st.documentSettings.minQuadrilateralAreaRatio = 2;
            mRouter.updateSettings("ReadPassportAndId", st);
        } catch (CaptureVisionRouterException e) { throw new RuntimeException(e); }

        mIrm = mRouter.getIntermediateResultManager();
        mIrm.addResultReceiver(mIntermediateReceiver);
        mRouter.addResultReceiver(mResultReceiver);
    }

    @Override protected void onResume() {
        super.onResume();
        mText = ""; mPendingLabelMap = null; mPendingPortraitBitmap = null;
        clearUnits();
        btnCapture.setEnabled(false); progressIndicator.setVisibility(View.VISIBLE);
        mCamera.open();
        mRouter.startCapturing("ReadPassportAndId", new CompletionListener() {
            @Override public void onSuccess() { runOnUiThread(() -> updateStatus("Ready")); }
            @Override public void onFailure(int c, String e) { runOnUiThread(() -> showDialog("Error", c + " " + e)); }
        });
    }

    @Override protected void onPause() { super.onPause(); mCamera.close(); mRouter.stopCapturing(); }
    @Override protected void onStop() {
        mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).clearDrawingItems();
        mCameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID).clearDrawingItems();
        super.onStop();
    }
    @Override protected void onDestroy() { super.onDestroy(); mIrm.removeResultReceiver(mIntermediateReceiver); mRouter.removeResultReceiver(mResultReceiver); }

    private void onCapture() {
        if (mPendingLabelMap == null) { Toast.makeText(this, "No MRZ data", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(this, ScanResultActivity.class);
        i.putExtra(ScanResultActivity.EXTRA_LABEL_MAP, mPendingLabelMap);
        if (mPendingPortraitBitmap != null) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            mPendingPortraitBitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
            i.putExtra(ScanResultActivity.EXTRA_PORTRAIT_BYTES, os.toByteArray());
        }
        startActivity(i);
    }

    private static Bitmap deskewPortrait(Bitmap src, Quadrilateral q) {
        if (src == null || q == null || q.points == null || q.points.length < 4) return null;
        Point[] pts = q.points;
        float[] sp = { pts[0].x, pts[0].y, pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y };
        float w = Math.max((float)Math.hypot(pts[1].x-pts[0].x, pts[1].y-pts[0].y), (float)Math.hypot(pts[2].x-pts[3].x, pts[2].y-pts[3].y));
        float h = Math.max((float)Math.hypot(pts[3].x-pts[0].x, pts[3].y-pts[0].y), (float)Math.hypot(pts[2].x-pts[1].x, pts[2].y-pts[1].y));
        if (w <= 0 || h <= 0) return null;
        int iw = Math.round(w);
        int ih = Math.round(h);
        float[] dp = { 0, 0, iw, 0, iw, ih, 0, ih };
        android.graphics.Matrix m = new android.graphics.Matrix();
        if (!m.setPolyToPoly(sp, 0, dp, 0, 4)) return null;
        Bitmap result = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(src, m, null);
        return result;
    }

    private void clearUnits() { mScaledColourImageUnit=null; mLocalizedTextLinesUnit=null; mRecognizedTextLinesUnit=null; mDetectedQuadsUnit=null; mDeskewedImageUnit=null; }
    private void updateStatus(String s) { if (tvStatus != null) tvStatus.setText(s); }
    private void showDialog(String t, String m) { if (mAlertDialog==null) mAlertDialog=new AlertDialog.Builder(this).setCancelable(true).setPositiveButton("OK",null).create(); mAlertDialog.setTitle(t); mAlertDialog.setMessage(m); mAlertDialog.show(); }
}
