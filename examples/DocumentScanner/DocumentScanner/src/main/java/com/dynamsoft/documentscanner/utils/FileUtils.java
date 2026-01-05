package com.dynamsoft.documentscanner.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.dynamsoft.core.basic_structures.CoreException;
import com.dynamsoft.core.basic_structures.EnumImageFileFormat;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.documentscanner.R;
import com.dynamsoft.utility.ImageIO;
import com.dynamsoft.utility.UtilityException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class FileUtils {

    /**
     * Saves an imageData object to the device's photo gallery.
     *
     * <p>This method handles saving images to the gallery for devices running on both
     * Android Q (API 29) and above, as well as older Android versions. For Android Q and above,
     * it uses the {@link MediaStore} API to store images in the Pictures directory,
     * making them visible in the gallery immediately. For older versions,
     * it saves the image to the app's private external directory and may require
     * additional steps to make them accessible in the gallery.</p>
     *
     * @param context The context used for accessing resources and system services.
     * @param image   The imageData object to be saved.
     * @throws IOException      If an error occurs while writing the image file.
     * @throws CoreException    If an unexpected issue occurs with {@code image.toBitmap()}.
     * @throws UtilityException If an unexpected issue occurs with {@code new ImageManager().saveToFile()}.
     */
    public static void saveImageToGallery(Context context, ImageData image) throws IOException, CoreException, UtilityException {
        String fileName = "Dynamsoft_normalize_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 and above
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            ContentResolver resolver = context.getContentResolver();
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            assert imageUri != null;
            OutputStream outputStream = resolver.openOutputStream(imageUri);
            if(outputStream != null) {
                byte[] bytes = new ImageIO().saveToMemory(image, EnumImageFileFormat.IFF_PNG);
                outputStream.write(bytes);
                outputStream.flush();
                outputStream.close();
                Toast.makeText(context, R.string.save_to_album_tip, Toast.LENGTH_SHORT).show();
            }
        } else {
            // Android 9 and below
            File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File outFile = new File(picturesDir, fileName);
            new ImageIO().saveToFile(image, outFile.getAbsolutePath(), true);
            Toast.makeText(context, "Successfully saved to " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

}
