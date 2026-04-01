package com.dynamsoft.documentscanner.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.documentscanner.scan.DocumentPage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileUtils {

    public static Uri exportToPdf(Context context, List<DocumentPage> pages) throws IOException, CoreException {
        if (pages == null || pages.isEmpty()) return null;

        PdfDocument pdfDocument = new PdfDocument();

        try {
            for (int i = 0; i < pages.size(); i++) {
                DocumentPage page = pages.get(i);
                Bitmap bitmap = page.getDisplayBitmap();
                if (bitmap == null) continue;

                int pageWidth = bitmap.getWidth();
                int pageHeight = bitmap.getHeight();

                // Scale to reasonable PDF dimensions (max 2480x3508 = A4 at 300dpi)
                float scale = 1.0f;
                if (pageWidth > 2480 || pageHeight > 3508) {
                    scale = Math.min(2480f / pageWidth, 3508f / pageHeight);
                    pageWidth = Math.round(pageWidth * scale);
                    pageHeight = Math.round(pageHeight * scale);
                }

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create();
                PdfDocument.Page pdfPage = pdfDocument.startPage(pageInfo);

                Canvas canvas = pdfPage.getCanvas();
                if (scale != 1.0f) {
                    canvas.scale(scale, scale);
                }
                canvas.drawBitmap(bitmap, 0, 0, null);

                pdfDocument.finishPage(pdfPage);
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "DocScan_" + timestamp + ".pdf";

            File documentsDir = new File(context.getFilesDir(), "documents");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }

            File pdfFile = new File(documentsDir, fileName);
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.flush();
            fos.close();

            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", pdfFile);
        } finally {
            pdfDocument.close();
        }
    }

    public static void openPdf(Context context, Uri pdfUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            // Try with chooser
            Intent chooser = Intent.createChooser(intent, "Open PDF");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(chooser);
            } catch (Exception e) {
                Toast.makeText(context, R.string.no_pdf_viewer, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Saves each page as a JPEG image to the device gallery.
     * Returns the number of pages successfully saved.
     */
    public static int exportToImages(Context context, List<DocumentPage> pages) throws CoreException, IOException {
        if (pages == null || pages.isEmpty()) return 0;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        int saved = 0;

        for (int i = 0; i < pages.size(); i++) {
            DocumentPage page = pages.get(i);
            Bitmap bitmap = page.getDisplayBitmap();
            if (bitmap == null) continue;

            String fileName = "DocScan_" + timestamp + "_" + (i + 1) + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.TITLE, fileName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/DocScanner");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            } else {
                File picturesDir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "DocScanner");
                if (!picturesDir.exists()) picturesDir.mkdirs();
                values.put(MediaStore.Images.Media.DATA,
                        new File(picturesDir, fileName).getAbsolutePath());
            }

            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) continue;

            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os);
                    saved++;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
        }

        return saved;
    }
}
