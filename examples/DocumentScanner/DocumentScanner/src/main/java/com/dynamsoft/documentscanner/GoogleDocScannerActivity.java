package com.dynamsoft.documentscanner;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Task;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GoogleDocScannerActivity extends AppCompatActivity {

    private static final String TAG = "GoogleDocScanner";

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private TextView tvPageInfo;
    private Button btnSaveImages;
    private Button btnDone;
    private ImageView ivBack;
    
    private List<Uri> imageUris = new ArrayList<>();
    private Uri pdfUri;
    private ImagePagerAdapter pagerAdapter;

    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    GmsDocumentScanningResult scanningResult =
                            GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                    
                    if (scanningResult != null) {
                        handleScanResult(scanningResult);
                    } else {
                        showError(getString(R.string.scan_failed));
                        finish();
                    }
                } else {
                    Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_doc_scanner);

        initViews();
        startDocumentScanner();
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);
        tvPageInfo = findViewById(R.id.tv_page_info);
        btnSaveImages = findViewById(R.id.btn_save_images);
        btnDone = findViewById(R.id.btn_done);
        ivBack = findViewById(R.id.iv_back);

        ivBack.setOnClickListener(v -> finish());
        
        btnSaveImages.setOnClickListener(v -> saveImagesToGallery());
        
        btnDone.setOnClickListener(v -> finish());

        // Set up ViewPager
        pagerAdapter = new ImagePagerAdapter(this, imageUris);
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager2 for page indicators
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // Dots as indicators, no text needed
        }).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageInfo(position);
            }
        });
    }

    private void startDocumentScanner() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(10)
                .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();

        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);

        Task<android.content.IntentSender> intentSenderTask = scanner.getStartScanIntent(this);
        intentSenderTask
                .addOnSuccessListener(intentSender -> {
                    IntentSenderRequest request = new IntentSenderRequest.Builder(intentSender).build();
                    scannerLauncher.launch(request);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to start scanner", e);
                    showError(getString(R.string.scan_failed) + ": " + e.getMessage());
                    finish();
                });
    }

    private void handleScanResult(GmsDocumentScanningResult result) {
        imageUris.clear();

        // Get scanned pages
        List<GmsDocumentScanningResult.Page> pages = result.getPages();
        if (pages != null && !pages.isEmpty()) {
            for (GmsDocumentScanningResult.Page page : pages) {
                Uri imageUri = page.getImageUri();
                if (imageUri != null) {
                    imageUris.add(imageUri);
                }
            }
        }

        // Get PDF
        GmsDocumentScanningResult.Pdf pdf = result.getPdf();
        if (pdf != null) {
            pdfUri = pdf.getUri();
            Log.d(TAG, "PDF generated with " + pdf.getPageCount() + " pages");
        }

        if (imageUris.isEmpty()) {
            showError(getString(R.string.no_pages));
            finish();
            return;
        }

        // Update UI
        pagerAdapter.updateImages(imageUris);
        updatePageInfo(0);
        
        // Show success message
        Toast.makeText(this, 
                getString(R.string.pages_scanned, imageUris.size()), 
                Toast.LENGTH_SHORT).show();
    }

    private void updatePageInfo(int position) {
        if (!imageUris.isEmpty()) {
            tvPageInfo.setText(String.format(Locale.getDefault(), 
                    "Page %d of %d", position + 1, imageUris.size()));
        }
    }

    private void saveImagesToGallery() {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, R.string.no_pages, Toast.LENGTH_SHORT).show();
            return;
        }

        int savedCount = 0;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        for (int i = 0; i < imageUris.size(); i++) {
            Uri uri = imageUris.get(i);
            try {
                Bitmap bitmap = loadBitmapFromUri(uri);
                if (bitmap != null) {
                    String fileName = "DocScan_" + timestamp + "_" + (i + 1) + ".jpg";
                    if (saveBitmapToGallery(bitmap, fileName)) {
                        savedCount++;
                    }
                    bitmap.recycle();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save image " + i, e);
            }
        }

        if (savedCount > 0) {
            Toast.makeText(this, R.string.save_to_album_tip, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                return bitmap;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load bitmap from URI", e);
        }
        return null;
    }

    private boolean saveBitmapToGallery(Bitmap bitmap, String fileName) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, 
                    Environment.DIRECTORY_PICTURES + "/DocScanner");
        }

        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        if (imageUri != null) {
            try {
                OutputStream outputStream = resolver.openOutputStream(imageUri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                    outputStream.close();
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to save bitmap", e);
            }
        }
        return false;
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
