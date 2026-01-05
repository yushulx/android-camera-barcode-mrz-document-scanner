package com.test.mrzscanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.license.LicenseManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays scan results from the document scanner.
 * Processes the scanned image using:
 * - Google ML Kit Face Detection for avatar extraction
 * - Google ML Kit Text Recognition for MRZ OCR
 * - Optionally Dynamsoft SDK for enhanced MRZ parsing
 */
public class ScanResultActivity extends AppCompatActivity {

    private static final String TAG = "ScanResultActivity";

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_USE_DYNAMSOFT = "use_dynamsoft";
    public static final String EXTRA_LABEL_MAP = "labelMap";

    // Views
    private ImageView ivAvatar;
    // private ImageView ivVerifiedBadge; // Removed
    private ImageView ivDocument;
    private TextView tvName;
    private TextView tvDocumentType;
    private TextView tvNationality;
    private TextView tvSex;
    private TextView tvAge;
    private TextView tvDocumentNumber;
    private TextView tvIssuingState;
    private TextView tvBirthDate;
    private TextView tvExpiryDate;
    private TextView tvMrzRaw;
    private CardView cardMrzRaw;
    private CardView cardDocumentImage;
    private CardView cardDocumentInfo;
    private CardView cardProfile;
    private View loadingOverlay;
    private TextView tvLoadingMessage;
    private MaterialButton btnScanAgain;
    private MaterialButton btnDone;
    private Toolbar toolbar;

    // ML Kit
    private FaceDetector faceDetector;
    private TextRecognizer textRecognizer;

    // Dynamsoft
    private CaptureVisionRouter mRouter;
    // private boolean useDynamsoft = false; // Removed

    // Data
    private Uri imageUri;
    private Bitmap documentBitmap;
    private Bitmap faceBitmap;
    private MrzParser.MrzData mrzData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        initViews();
        initMLKit();
        processIntent();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivAvatar = findViewById(R.id.iv_avatar);
        // ivVerifiedBadge = findViewById(R.id.iv_verified_badge); // Removed
        ivDocument = findViewById(R.id.iv_document);
        tvName = findViewById(R.id.tv_name);
        tvDocumentType = findViewById(R.id.tv_document_type);
        tvNationality = findViewById(R.id.tv_nationality);
        tvSex = findViewById(R.id.tv_sex);
        tvAge = findViewById(R.id.tv_age);
        tvDocumentNumber = findViewById(R.id.tv_document_number);
        tvIssuingState = findViewById(R.id.tv_issuing_state);
        tvBirthDate = findViewById(R.id.tv_birth_date);
        tvExpiryDate = findViewById(R.id.tv_expiry_date);
        tvMrzRaw = findViewById(R.id.tv_mrz_raw);
        cardMrzRaw = findViewById(R.id.card_mrz_raw);
        cardDocumentImage = findViewById(R.id.card_document_image);
        cardDocumentInfo = findViewById(R.id.card_document_info);
        cardProfile = findViewById(R.id.card_profile);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingMessage = findViewById(R.id.tv_loading_message);
        btnScanAgain = findViewById(R.id.btn_scan_again);
        btnDone = findViewById(R.id.btn_done);

        // Setup toolbar
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup button listeners
        btnScanAgain.setOnClickListener(v -> {
            finish();
        });

        btnDone.setOnClickListener(v -> {
            // Return to home
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });
    }

    private void initMLKit() {
        // Initialize Face Detector
        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.1f)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        // Initialize Text Recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    private void initDynamsoft() {
        // Initialize Dynamsoft License
        MrzUtils.initLicense();

        mRouter = new CaptureVisionRouter();
    }

    private void processIntent() {
        Intent intent = getIntent();

        // Check if coming from live scan with pre-parsed data
        if (intent.hasExtra(EXTRA_LABEL_MAP)) {
            @SuppressWarnings("unchecked")
            HashMap<String, String> labelMap = (HashMap<String, String>) intent.getSerializableExtra(EXTRA_LABEL_MAP);
            if (labelMap != null) {
                displayParsedData(labelMap);
                return;
            }
        }

        // Process scanned document image
        String uriString = intent.getStringExtra(EXTRA_IMAGE_URI);
        // useDynamsoft = intent.getBooleanExtra(EXTRA_USE_DYNAMSOFT, false); // Removed

        if (uriString != null) {
            imageUri = Uri.parse(uriString);
            processScannedDocument();
        } else {
            Toast.makeText(this, "No image to process", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void processScannedDocument() {
        showLoading("Loading document...");

        try {
            // Load the document bitmap
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            documentBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (documentBitmap == null) {
                hideLoading();
                Toast.makeText(this, "Failed to load document image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show document image
            cardDocumentImage.setVisibility(View.VISIBLE);
            ivDocument.setImageBitmap(documentBitmap);

            // Start parallel processing
            processImage();

        } catch (IOException e) {
            hideLoading();
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processImage() {
        processwithDynamsoft();
        showLoading("Detecting face...");

        InputImage inputImage = InputImage.fromBitmap(documentBitmap, 0);

        // First, detect faces
        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        extractFace(faces.get(0));
                    }

                    // Then recognize text
                    showLoading("Recognizing MRZ...");
                    recognizeText(inputImage);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed", e);
                    // Continue with text recognition even if face detection fails
                    showLoading("Recognizing MRZ...");
                    recognizeText(inputImage);
                });
    }

    private void extractFace(Face face) {
        try {
            Rect bounds = face.getBoundingBox();

            // Add padding around the face
            int padding = (int) (bounds.width() * 0.3);
            int left = Math.max(0, bounds.left - padding);
            int top = Math.max(0, bounds.top - padding);
            int right = Math.min(documentBitmap.getWidth(), bounds.right + padding);
            int bottom = Math.min(documentBitmap.getHeight(), bounds.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width > 0 && height > 0) {
                faceBitmap = Bitmap.createBitmap(documentBitmap, left, top, width, height);

                runOnUiThread(() -> {
                    ivAvatar.setImageBitmap(faceBitmap);
                    // ivVerifiedBadge.setVisibility(View.VISIBLE);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting face", e);
        }
    }

    private void recognizeText(InputImage inputImage) {

        textRecognizer.process(inputImage)
                .addOnSuccessListener(text -> {
                    String recognizedText = text.getText();
                    Log.d(TAG, "Recognized text: " + recognizedText);

                    // Always show the full recognized text
                    if (!recognizedText.isEmpty()) {
                        tvMrzRaw.setText(recognizedText);
                        cardMrzRaw.setVisibility(View.VISIBLE);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Text recognition failed", e);
                });
    }

    private String extractMrzFromText(Text text) {
        StringBuilder mrzBuilder = new StringBuilder();

        // MRZ typically has lines with specific patterns
        // TD1: 3 lines of 30 chars, TD2: 2 lines of 36 chars, TD3: 2 lines of 44 chars
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText().replaceAll("\\s+", "");

                // Check if this looks like an MRZ line
                // MRZ lines contain only A-Z, 0-9, and < characters
                if (isMrzLine(lineText)) {
                    mrzBuilder.append(lineText).append("\n");
                }
            }
        }

        return mrzBuilder.toString().trim();
    }

    private boolean isMrzLine(String line) {
        if (line.length() < 28) return false; // Minimum MRZ line length

        // Count valid MRZ characters
        int validChars = 0;
        for (char c : line.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<') {
                validChars++;
            }
        }

        // At least 90% should be valid MRZ characters
        return (double) validChars / line.length() >= 0.9;
    }

    private void processwithDynamsoft() {
        if (mRouter == null) {
            initDynamsoft();
        }

        new Thread(() -> {
            try {
                // Convert bitmap to ImageData
//                ImageData imageData = bitmapToImageData(documentBitmap);

                // Capture with Dynamsoft
                CapturedResult result = mRouter.capture(documentBitmap, "ReadPassportAndId");

                ParsedResult parsedResult = result.getParsedResult();
                RecognizedTextLinesResult textResult = result.getRecognizedTextLinesResult();

                runOnUiThread(() -> {
                    if (parsedResult != null && parsedResult.getItems() != null
                            && parsedResult.getItems().length > 0) {
                        // Use Dynamsoft parsed result - show document info card
                        cardDocumentInfo.setVisibility(View.VISIBLE);
                        displayDynamsoftResult(parsedResult.getItems()[0], textResult);
                    } else if (textResult != null && textResult.getItems() != null
                            && textResult.getItems().length > 0) {
                        // Try to parse the recognized text
                        StringBuilder mrzBuilder = new StringBuilder();
                        for (TextLineResultItem item : textResult.getItems()) {
                            mrzBuilder.append(item.getText()).append("\n");
                        }
                        String mrzText = mrzBuilder.toString();
                        mrzData = MrzParser.parse(mrzText);
                        
                        if (mrzData != null && mrzData.isValid) {
                            cardDocumentInfo.setVisibility(View.VISIBLE);
                            displayResults();
                        } else {
                            hideLoading();
                            cardDocumentInfo.setVisibility(View.GONE);
                            tvName.setText("Document Scanned");
                            tvDocumentType.setText("DOCUMENT");
                            
                            // Show the text from Dynamsoft if available
                            if (!mrzText.isEmpty()) {
                                tvMrzRaw.setText(mrzText.trim());
                                cardMrzRaw.setVisibility(View.VISIBLE);
                            }
                        }
                    } else {
                        hideLoading();
                        cardDocumentInfo.setVisibility(View.GONE);
                        tvName.setText("Document Scanned");
                        tvDocumentType.setText("DOCUMENT");
                        Toast.makeText(this, "Could not parse MRZ with Dynamsoft", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Dynamsoft processing failed", e);
                runOnUiThread(() -> {
                    hideLoading();
                    cardDocumentInfo.setVisibility(View.GONE);
                    tvName.setText("Document Scanned");
                    tvDocumentType.setText("DOCUMENT");
                    // Don't show error toast to user, just log it. Fallback to ML Kit text is already shown.
                    // Toast.makeText(this, "Dynamsoft processing failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private ImageData bitmapToImageData(Bitmap bitmap) {
        // Convert ARGB_8888 to grayscale or appropriate format
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // Convert to ARGB bytes
        byte[] bytes = new byte[width * height * 4];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            bytes[i * 4] = (byte) ((pixel >> 16) & 0xFF); // R
            bytes[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF); // G
            bytes[i * 4 + 2] = (byte) (pixel & 0xFF); // B
            bytes[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }

        ImageData imageData = new ImageData();
        imageData.bytes = bytes;
        imageData.width = width;
        imageData.height = height;
        imageData.stride = width * 4;
        imageData.format = 1; // ARGB_8888

        return imageData;
    }

    private void displayDynamsoftResult(ParsedResultItem item, RecognizedTextLinesResult textResult) {
        HashMap<String, String> data = MrzUtils.parseDynamsoftResult(item);
        displayParsedData(data);

        // Display MRZ raw text
        if (textResult != null && textResult.getItems() != null) {
            StringBuilder mrzBuilder = new StringBuilder();
            for (TextLineResultItem textItem : textResult.getItems()) {
                mrzBuilder.append(textItem.getText()).append("\n");
            }
            tvMrzRaw.setText(mrzBuilder.toString().trim());
            cardMrzRaw.setVisibility(View.VISIBLE);
        }
    }

    private void displayResults() {
        hideLoading();

        if (mrzData == null || !mrzData.isValid) {
            cardDocumentInfo.setVisibility(View.GONE);
            tvName.setText("Document Scanned");
            tvDocumentType.setText("DOCUMENT");
            Toast.makeText(this, "Could not parse MRZ data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show document info card when we have valid parsed data
        cardDocumentInfo.setVisibility(View.VISIBLE);
        tvDocumentType.setText(mrzData.documentType);
        tvName.setText(mrzData.fullName.isEmpty() ? "—" : mrzData.fullName);
        tvNationality.setText(mrzData.nationality.isEmpty() ? "—" : mrzData.nationality);
        tvSex.setText(mrzData.sex.isEmpty() ? "—" : mrzData.sex);
        tvAge.setText(mrzData.age >= 0 ? String.valueOf(mrzData.age) : "—");
        tvDocumentNumber.setText(mrzData.documentNumber.isEmpty() ? "—" : mrzData.documentNumber);
        tvIssuingState.setText(mrzData.issuingState.isEmpty() ? "—" : mrzData.issuingState);
        tvBirthDate.setText(mrzData.dateOfBirth.isEmpty() ? "—" : mrzData.dateOfBirth);
        tvExpiryDate.setText(mrzData.dateOfExpiry.isEmpty() ? "—" : mrzData.dateOfExpiry);

        // Show raw MRZ
        if (!mrzData.rawMrz.isEmpty()) {
            tvMrzRaw.setText(mrzData.rawMrz);
            cardMrzRaw.setVisibility(View.VISIBLE);
        }
    }

    private void displayParsedData(HashMap<String, String> data) {
        cardDocumentInfo.setVisibility(View.VISIBLE); // Ensure visibility
        
        tvDocumentType.setText(data.getOrDefault("Document Type", "—"));
        tvName.setText(data.getOrDefault("Name", "—"));
        tvNationality.setText(data.getOrDefault("Nationality", "—"));
        tvSex.setText(data.getOrDefault("Sex", "—")); // Already formatted in MrzUtils
        tvAge.setText(data.getOrDefault("Age", "—"));
        tvDocumentNumber.setText(data.getOrDefault("Document Number", "—"));
        tvIssuingState.setText(data.getOrDefault("Issuing State", "—"));
        tvBirthDate.setText(data.getOrDefault("Date of Birth(YYYY-MM-DD)", "—"));
        tvExpiryDate.setText(data.getOrDefault("Date of Expiry(YYYY-MM-DD)", "—"));

        hideLoading();
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            tvLoadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
