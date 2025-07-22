package com.dynamsoft.scanmrz;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.dynamsoft.mrzscannerbundle.ui.CommonResult;
import com.dynamsoft.mrzscannerbundle.ui.EnumDetectionType;
import com.dynamsoft.mrzscannerbundle.ui.EnumScanMode;
import com.dynamsoft.mrzscannerbundle.ui.MRZData;
import com.dynamsoft.mrzscannerbundle.ui.MRZScanResult;
import com.dynamsoft.mrzscannerbundle.ui.ScannerActivity;
import com.dynamsoft.mrzscannerbundle.ui.ScannerConfig;
import com.dynamsoft.mrzscannerbundle.ui.VINData;
import com.dynamsoft.mrzscannerbundle.ui.VINScanResult;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.File;
import java.util.HashMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

// Place at the top after package declaration
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
// Correct import
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.io.image.ImageDataFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.os.Environment;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Typeface;

public class MainActivity extends AppCompatActivity {
	private ImageView btnSharePdf;
	private ImageView btnOpenPdf;
	private ImageView ivFace;
    private ImageView ivDocument;
    private Bitmap faceBitmap;
    private Bitmap documentBitmap;
	private List<String> scannedInfo = new ArrayList<>();
	private String currentDocType;
	private File lastCreatedPdfFile;
	private ActivityResultLauncher<ScannerConfig> launcher;
	private LinearLayout content;
	private MaterialButtonToggleGroup toggleGroup;
	private RadioGroup radioGroup;
	ScannerConfig config;
	private final ActivityResultLauncher<Intent> cameraXLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					Intent data = result.getData();
					int statusCode = data.getIntExtra("status_code", 0);

					if (statusCode == 1) { // Success
						String docType = data.getStringExtra("doc_type");
						HashMap<String, String> resultData = (HashMap<String, String>) data.getSerializableExtra("result");

						content.removeAllViews();
						scannedInfo.clear();
						faceBitmap = null;
						currentDocType = docType;
						btnSharePdf.setVisibility(View.VISIBLE);
						btnOpenPdf.setVisibility(View.VISIBLE); // Show both buttons together
						ivFace.setVisibility(View.GONE);

						// Add header
						TextView header = new TextView(this);
						header.setText("Scan Results");
						header.setTextSize(20);
						header.setTypeface(null, Typeface.BOLD);
						header.setTextColor(ContextCompat.getColor(this, android.R.color.black));
						header.setPadding(16, 16, 16, 16);
						content.addView(header);

						LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
						params.setMargins(16, 8, 16, 8);

						if ("VIN".equals(docType)) {
							// Handle VIN results
							content.addView(childView("VIN:", resultData.get("vinString")), params);
							scannedInfo.add("VIN: " + resultData.get("vinString"));
							content.addView(childView("WMI:", resultData.get("wmi")), params);
							scannedInfo.add("WMI: " + resultData.get("wmi"));
							content.addView(childView("Region:", resultData.get("region")), params);
							scannedInfo.add("Region: " + resultData.get("region"));
							content.addView(childView("VDS:", resultData.get("vds")), params);
							scannedInfo.add("VDS: " + resultData.get("vds"));
							content.addView(childView("Check Digit:", resultData.get("checkDigit")), params);
							scannedInfo.add("Check Digit: " + resultData.get("checkDigit"));
							content.addView(childView("Model Year:", resultData.get("modelYear")), params);
							scannedInfo.add("Model Year: " + resultData.get("modelYear"));
							content.addView(childView("Plant Code:", resultData.get("plantCode")), params);
							scannedInfo.add("Plant Code: " + resultData.get("plantCode"));
							content.addView(childView("Serial Number:", resultData.get("serialNumber")), params);
							scannedInfo.add("Serial Number: " + resultData.get("serialNumber"));
						} else {
							// Handle MRZ results
							String firstName = resultData.get("firstName") != null ? resultData.get("firstName") : "";
							String lastName = resultData.get("lastName") != null ? resultData.get("lastName") : "";
							String sex = resultData.get("sex");
							String age = resultData.get("age");
							String documentType = resultData.get("documentType");
							String documentNumber = data.getStringExtra("number");
							String issuingState = data.getStringExtra("issuing_state");
							String nationality = data.getStringExtra("nationality");
							String dateOfBirth = resultData.get("dateOfBirth");
							String dateOfExpiry = resultData.get("dateOfExpiry");

							// Check if face image is available and display it
                            String faceImagePath = data.getStringExtra("face_image_path");
                            if (faceImagePath != null && !faceImagePath.isEmpty()) {
                                displayFaceImage(faceImagePath);
                            }

                            String documentImagePath = data.getStringExtra("document_image_path");
                            if (documentImagePath != null && !documentImagePath.isEmpty()) {
                                displayDocumentImage(documentImagePath);
                            }
                            
                            // For backward compatibility
                            if (faceImagePath == null) {
                                String faceImageBase64 = data.getStringExtra("face_image");
                                if (faceImageBase64 != null && !faceImageBase64.isEmpty()) {
                                    // Convert Base64 to bitmap and display
                                    byte[] decodedString = Base64.decode(faceImageBase64, Base64.DEFAULT);
                                    BitmapFactory.Options options = new BitmapFactory.Options();
                                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                    faceBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length, options);
                                    ivFace.setImageBitmap(faceBitmap);
                                    ivFace.setVisibility(View.VISIBLE);
                                    ivFace.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                }
                            }
                            
                            if (documentImagePath == null) {
                                String documentImageBase64 = data.getStringExtra("document_image");
                                if (documentImageBase64 != null && !documentImageBase64.isEmpty()) {
                                    // Convert Base64 to bitmap and display
                                    try {
                                        byte[] decodedString = Base64.decode(documentImageBase64, Base64.DEFAULT);
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                                        documentBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length, options);
                                        if (documentBitmap != null) {
                                            ivDocument.setImageBitmap(documentBitmap);
                                            ivDocument.setVisibility(View.VISIBLE);
                                            ivDocument.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

							content.addView(childView("Name:", firstName + " " + lastName), params);
							scannedInfo.add("Name: " + firstName + " " + lastName);
							content.addView(childView("Sex:", sex != null && !sex.isEmpty() ?
									sex.substring(0, 1).toUpperCase() + sex.substring(1) : ""), params);
							scannedInfo.add("Sex: " + (sex != null && !sex.isEmpty() ? sex.substring(0, 1).toUpperCase() + sex.substring(1) : ""));
							content.addView(childView("Age:", age != null ? age : ""), params);
							scannedInfo.add("Age: " + (age != null ? age : ""));
							content.addView(childView("Document Type:", documentType != null ? documentType : ""), params);
							scannedInfo.add("Document Type: " + (documentType != null ? documentType : ""));
							content.addView(childView("Document Number:", documentNumber != null ? documentNumber : ""), params);
							scannedInfo.add("Document Number: " + (documentNumber != null ? documentNumber : ""));
							content.addView(childView("Issuing State:", issuingState != null ? issuingState : ""), params);
							scannedInfo.add("Issuing State: " + (issuingState != null ? issuingState : ""));
							content.addView(childView("Nationality:", nationality != null ? nationality : ""), params);
							scannedInfo.add("Nationality: " + (nationality != null ? nationality : ""));
							content.addView(childView("Date of Birth(YYYY-MM-DD):", dateOfBirth != null ? dateOfBirth : ""), params);
							scannedInfo.add("Date of Birth(YYYY-MM-DD): " + (dateOfBirth != null ? dateOfBirth : ""));
							content.addView(childView("Date of Expiry(YYYY-MM-DD):", dateOfExpiry != null ? dateOfExpiry : ""), params);
							scannedInfo.add("Date of Expiry(YYYY-MM-DD): " + (dateOfExpiry != null ? dateOfExpiry : ""));
						}
					}
				} else {
					content.removeAllViews();
					content.addView(childView("CameraX scan canceled.", ""));
					btnSharePdf.setVisibility(View.GONE);
					btnOpenPdf.setVisibility(View.GONE);
					ivFace.setVisibility(View.GONE);
				}
			});



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.ll_content);
        ivFace = findViewById(R.id.iv_face);
        ivDocument = findViewById(R.id.iv_document);
        
        // Clean up old cache files
        cleanupCacheFiles();
        btnSharePdf = findViewById(R.id.btn_share_pdf);
        btnOpenPdf = findViewById(R.id.btn_open_pdf);
        btnSharePdf.setOnClickListener(v -> sharePdf());
        btnOpenPdf.setOnClickListener(v -> openPdf());

        config = new ScannerConfig();
        config.setLicense("DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==");
        config.setTorchButtonVisible(true);
        config.setCloseButtonVisible(true);

        launcher = registerForActivityResult(new ScannerActivity.ResultContract(), commonResult -> {
            if (documentBitmap != null) {
                documentBitmap = null; // Clear previous document image if any
            }

            if (commonResult == null) return;

            // In the launcher result callback:
            if (commonResult.getResultStatus() == CommonResult.EnumResultStatus.RS_FINISHED) {
                scannedInfo.clear();
                faceBitmap = null;
                btnSharePdf.setVisibility(View.VISIBLE);
                btnOpenPdf.setVisibility(View.VISIBLE); // Show both buttons together
                ivFace.setVisibility(View.GONE);
                ivDocument.setVisibility(View.GONE);
                switch (commonResult.getDetectionType()) {
                    case MRZ:
                        MRZScanResult result = (MRZScanResult) commonResult;
                        if (result.getData() != null) {
                            MRZData data = result.getData();
                            content.removeAllViews();
                             ivFace.setVisibility(View.GONE);
                             ivDocument.setVisibility(View.GONE);
                            
                            // Face image handling is done through CameraXActivity intent extras
                            // Header and child views for MRZ
                            TextView header = new TextView(this);
                            header.setText("Scan Results");
                            header.setTextSize(20);
                            header.setTypeface(null, Typeface.BOLD);
                            header.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                            header.setPadding(16, 16, 16, 16);
                            content.addView(header);
                        
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.setMargins(16, 8, 16, 8);
                        
                            View nameView = childView("Name:", data.getFirstName() + " " + data.getLastName());
                            content.addView(nameView, params);
                            scannedInfo.add("Name: " + data.getFirstName() + " " + data.getLastName());
                        
                            View sexView = childView("Sex:", data.getSex() == null ? "" : data.getSex().substring(0, 1).toUpperCase() + data.getSex().substring(1));
                            content.addView(sexView, params);
                            scannedInfo.add("Sex: " + (data.getSex() == null ? "" : data.getSex().substring(0, 1).toUpperCase() + data.getSex().substring(1)));
                        
                            View ageView = childView("Age:", data.getAge() + "");
                            content.addView(ageView, params);
                            scannedInfo.add("Age: " + data.getAge());
                        
                            View docTypeView = childView("Document Type:", data.getDocumentType());
                            content.addView(docTypeView, params);
                            scannedInfo.add("Document Type: " + data.getDocumentType());
                        
                            View docNumberView = childView("Document Number:", data.getDocumentNumber());
                            content.addView(docNumberView, params);
                            scannedInfo.add("Document Number: " + data.getDocumentNumber());
                        
                            View issuingStateView = childView("Issuing State:", data.getIssuingState());
                            content.addView(issuingStateView, params);
                            scannedInfo.add("Issuing State: " + data.getIssuingState());
                        
                            View nationalityView = childView("Nationality:", data.getNationality());
                            content.addView(nationalityView, params);
                            scannedInfo.add("Nationality: " + data.getNationality());
                        
                            View dobView = childView("Date of Birth(YYYY-MM-DD):", data.getDateOfBirth());
                            content.addView(dobView, params);
                            scannedInfo.add("Date of Birth(YYYY-MM-DD): " + data.getDateOfBirth());
                        
                            View doeView = childView("Date of Expiry(YYYY-MM-DD):", data.getDateOfExpire());
                            content.addView(doeView, params);
                            scannedInfo.add("Date of Expiry(YYYY-MM-DD): " + data.getDateOfExpire());
                        }
                        break;
                    case VIN:
                        VINScanResult vinResult = (VINScanResult) commonResult;
                        if (vinResult.getData() != null) {
                            VINData data = vinResult.getData();
                            content.removeAllViews();
                            ivFace.setVisibility(View.GONE);
                            ivDocument.setVisibility(View.GONE);
                            TextView header = new TextView(this);
                            header.setText("Scan Results");
                            header.setTextSize(20);
                            header.setTypeface(null, Typeface.BOLD);
                            header.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                            header.setPadding(16, 16, 16, 16);
                            content.addView(header);
                        
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.setMargins(16, 8, 16, 8);
                        
                            View vinView = childView("VIN:", data.vinString);
                            content.addView(vinView, params);
                            scannedInfo.add("VIN: " + data.vinString);
                        
                            View wmiView = childView("WMI:", data.wmi);
                            content.addView(wmiView, params);
                            scannedInfo.add("WMI: " + data.wmi);
                        
                            View regionView = childView("Region:", data.region);
                            content.addView(regionView, params);
                            scannedInfo.add("Region: " + data.region);
                        
                            View vdsView = childView("VDS:", data.vds);
                            content.addView(vdsView, params);
                            scannedInfo.add("VDS: " + data.vds);
                        
                            View checkDigitView = childView("Check Digit:", data.checkDigit);
                            content.addView(checkDigitView, params);
                            scannedInfo.add("Check Digit: " + data.checkDigit);
                        
                            View modelYearView = childView("Model Year:", data.modelYear);
                            content.addView(modelYearView, params);
                            scannedInfo.add("Model Year: " + data.modelYear);
                        
                            View plantCodeView = childView("Plant Code:", data.plantCode);
                            content.addView(plantCodeView, params);
                            scannedInfo.add("Plant Code: " + data.plantCode);
                        
                            View serialNumberView = childView("Serial Number:", data.serialNumber);
                            content.addView(serialNumberView, params);
                            scannedInfo.add("Serial Number: " + data.serialNumber);
                        }
                        break;
                }
            } else if (commonResult.getResultStatus() == CommonResult.EnumResultStatus.RS_CANCELED) {
                content.removeAllViews();
                content.addView(childView("Scan canceled.", ""));
                btnSharePdf.setVisibility(View.GONE);
                btnOpenPdf.setVisibility(View.GONE);
            }
            if (commonResult.getErrorString() != null && !commonResult.getErrorString().isEmpty()) {
                content.removeAllViews();
                content.addView(childView("Error:", commonResult.getErrorString()));
                btnSharePdf.setVisibility(View.GONE);
                btnOpenPdf.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.btn_camera_scan).setOnClickListener(v -> {
            config.setScanMode(EnumScanMode.CAMERA);
            launcher.launch(config);
        });

        findViewById(R.id.btn_picture_scan).setOnClickListener(v -> {
            config.setScanMode(EnumScanMode.PICTURE);
            launcher.launch(config);
        });

        findViewById(R.id.btn_camerax_scan).setOnClickListener(v -> {
            launchCameraXActivity();
        });

        radioGroup = findViewById(R.id.radio_group_mode);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_mrz) {
                config.setDetectionType(EnumDetectionType.MRZ);
            } else if (checkedId == R.id.radio_vin) {
                config.setDetectionType(EnumDetectionType.VIN);
            }
        });
    }

    @NonNull
    private View childView(String label, String labelText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setPadding(8, 8, 8, 8);
        layout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        layout.setElevation(4);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));
        layout.addView(tvLabel);

        // Remove tvLabel.setTextStyle(Typeface.BOLD);

        // For headers in all places, they already use setTypeface(null, Typeface.BOLD);
        TextView tvLabelText = new TextView(this);
        tvLabelText.setText(labelText);
        tvLabelText.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        tvLabelText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        layout.addView(tvLabelText);

        return layout;
    }

    private void launchCameraXActivity() {
        Intent intent = new Intent(this, CameraXActivity.class);
        intent.putExtra("scanner_config", config);
        cameraXLauncher.launch(intent);
    }

    private void cleanupCacheFiles() {
        File cacheDir = getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                // Delete files older than 24 hours
                long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                for (File file : files) {
                    if (file.lastModified() < cutoff) {
                        file.delete();
                    }
                }
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up cache files when activity is destroyed
        cleanupCacheFiles();
    }
    
    private void displayFaceImage(String faceImagePath) {
        if (faceImagePath == null) return;
        
        try {
            // Use options to maintain original quality
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            faceBitmap = BitmapFactory.decodeFile(faceImagePath, options);
            if (faceBitmap != null) {
                ivFace.setImageBitmap(faceBitmap);
                ivFace.setVisibility(View.VISIBLE);
                // Set scale type to maintain aspect ratio without distortion
                ivFace.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void displayDocumentImage(String documentImagePath) {
        if (documentImagePath == null) return;
        
        try {
            // Use options to maintain original quality
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            documentBitmap = BitmapFactory.decodeFile(documentImagePath, options);
            if (documentBitmap != null) {
                ivDocument.setImageBitmap(documentBitmap);
                ivDocument.setVisibility(View.VISIBLE);
                // Set scale type to maintain aspect ratio without distortion
                ivDocument.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sharePdf() {
        if (scannedInfo.isEmpty()) return;

        try {
            createPdf();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        btnOpenPdf.setVisibility(View.VISIBLE); // Show Open PDF button after creation
        Uri pdfUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", lastCreatedPdfFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share PDF"));
    }

    private void openPdf() {
        try {
            createPdf();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            Uri pdfUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", lastCreatedPdfFile);
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(pdfUri, "application/pdf");
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(openIntent);
        } catch (Exception e) {
            // If no PDF app is available or there's an error, show a message
            e.printStackTrace();
            // Optionally show a toast or dialog to inform the user
        }
    }

    private File createPdf() throws IOException {
        String fileName = currentDocType + "_Scan_" + System.currentTimeMillis() + ".pdf";
        File pdfFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        lastCreatedPdfFile = pdfFile; // Save the last created PDF file
        PdfWriter writer = new PdfWriter(pdfFile);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        // Add face image if available
        if (faceBitmap != null) {
            File tempImage = new File(getCacheDir(), "face.jpg");
            FileOutputStream fos = new FileOutputStream(tempImage);
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            Image faceImage = new Image(ImageDataFactory.create(tempImage.getAbsolutePath()));
            
            // Calculate appropriate size while maintaining aspect ratio
            float pageWidth = pdf.getDefaultPageSize().getWidth() - 50; // Margin
            float imageWidth = Math.min(pageWidth, 300); // Max width 300pt
            float aspectRatio = (float) faceBitmap.getWidth() / faceBitmap.getHeight();
            float imageHeight = imageWidth / aspectRatio;
            
            faceImage.setWidth(imageWidth);
            faceImage.setHeight(imageHeight);
            document.add(new Paragraph("Face Image:").setBold());
            document.add(faceImage);
            document.add(new Paragraph("\n"));
        }
        
        // Add document image if available
        if (documentBitmap != null) {
            File tempDocImage = new File(getCacheDir(), "document.jpg");
            FileOutputStream fos = new FileOutputStream(tempDocImage);
            documentBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            Image docImage = new Image(ImageDataFactory.create(tempDocImage.getAbsolutePath()));
            
            // Calculate appropriate size while maintaining aspect ratio
            float pageWidth = pdf.getDefaultPageSize().getWidth() - 50; // Margin
            float imageWidth = Math.min(pageWidth, 450); // Max width 450pt for document
            float aspectRatio = (float) documentBitmap.getWidth() / documentBitmap.getHeight();
            float imageHeight = imageWidth / aspectRatio;
            
            docImage.setWidth(imageWidth);
            docImage.setHeight(imageHeight);
            document.add(new Paragraph("Document Image:").setBold());
            document.add(docImage);
            document.add(new Paragraph("\n"));
        }
        
        for (String info : scannedInfo) {
            document.add(new Paragraph(info).setTextAlignment(TextAlignment.LEFT));
        }
        document.close();
        return pdfFile;
    }
}
