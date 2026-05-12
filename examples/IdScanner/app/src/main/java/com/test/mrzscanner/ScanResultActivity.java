package com.test.mrzscanner;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.Map;

/**
 * Displays scan results from the live camera scanner.
 * Shows parsed MRZ data and detected portrait (face) image
 * using Dynamsoft Capture Vision Identity Processor.
 */
public class ScanResultActivity extends AppCompatActivity {

    private static final String TAG = "ScanResultActivity";

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_LABEL_MAP = "labelMap";
    public static final String EXTRA_PORTRAIT_BYTES = "portrait_bytes";

    // Views
    private ImageView ivAvatar;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_result);

        initViews();
        processIntent();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivAvatar = findViewById(R.id.iv_avatar);
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

        toolbar.setNavigationOnClickListener(v -> finish());

        btnScanAgain.setOnClickListener(v -> finish());

        btnDone.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });
    }

    private void processIntent() {
        Intent intent = getIntent();

        // Check if coming from live scan with pre-parsed data
        if (intent.hasExtra(EXTRA_LABEL_MAP)) {
            @SuppressWarnings("unchecked")
            HashMap<String, String> labelMap = null;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                java.io.Serializable ser = intent.getSerializableExtra(EXTRA_LABEL_MAP, java.io.Serializable.class);
                if (ser instanceof HashMap) labelMap = (HashMap<String, String>) ser;
            } else {
                labelMap = (HashMap<String, String>) intent.getSerializableExtra(EXTRA_LABEL_MAP);
            }
            if (labelMap != null) {
                // Display portrait if available
                byte[] portraitBytes = intent.getByteArrayExtra(EXTRA_PORTRAIT_BYTES);
                if (portraitBytes != null && portraitBytes.length > 0) {
                    Bitmap portraitBitmap = BitmapFactory.decodeByteArray(
                            portraitBytes, 0, portraitBytes.length);
                    if (portraitBitmap != null) {
                        ivAvatar.setImageBitmap(portraitBitmap);
                    }
                }

                displayParsedData(labelMap);
                return;
            }
        }

        // Legacy: process scanned document image URI (fallback)
        String uriString = intent.getStringExtra(EXTRA_IMAGE_URI);
        if (uriString != null) {
            Uri imageUri = Uri.parse(uriString);
            try {
                java.io.InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) inputStream.close();
                if (bitmap != null) {
                    cardDocumentImage.setVisibility(View.VISIBLE);
                    ivDocument.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error loading image", e);
            }
        }

        Toast.makeText(this, "No data to display", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ========================================================================
    // Display Helpers
    // ========================================================================

    private void displayParsedData(Map<String, String> data) {
        hideLoading();
        cardDocumentInfo.setVisibility(View.VISIBLE);

        tvDocumentType.setText(data.getOrDefault("Document Type", "—"));
        tvName.setText(data.getOrDefault("Name", "—"));
        tvNationality.setText(data.getOrDefault("Nationality", "—"));
        tvSex.setText(data.getOrDefault("Sex", "—"));
        tvAge.setText(data.getOrDefault("Age", "—"));
        tvDocumentNumber.setText(data.getOrDefault("Document Number", "—"));
        tvIssuingState.setText(data.getOrDefault("Issuing State", "—"));
        tvBirthDate.setText(data.getOrDefault("Date of Birth(YYYY-MM-DD)", "—"));
        tvExpiryDate.setText(data.getOrDefault("Date of Expiry(YYYY-MM-DD)", "—"));
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
}
