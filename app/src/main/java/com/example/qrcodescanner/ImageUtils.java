package com.example.qrcodescanner;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.os.Environment;

import androidx.camera.core.ImageProxy;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {
  private static final String TAG = "ImageUtils";

  public static void saveRGBMat(Mat rgb) {
    final Bitmap bitmap = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(rgb, bitmap);
    String filename = "test.png";
    File sd = Environment.getExternalStorageDirectory();
    File dest = new File(sd, filename);

    try {
      FileOutputStream out = new FileOutputStream(dest);
      bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
      out.flush();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // https://github.com/android/camera-samples/blob/3730442b49189f76a1083a98f3acf3f5f09222a3/CameraUtils/lib/src/main/java/com/example/android/camera/utils/YuvToRgbConverter.kt
  // https://stackoverflow.com/questions/30510928/convert-android-camera2-api-yuv-420-888-to-rgb
  public static Mat imageToMat(ImageProxy image, byte[] out) {
    ByteBuffer buffer;
    int rowStride;
    int pixelStride;
    int width = image.getWidth();
    int height = image.getHeight();
    int offset = 0;

    ImageProxy.PlaneProxy[] planes = image.getPlanes();
    byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
    byte[] rowData = new byte[planes[0].getRowStride()];

    for (int i = 0; i < planes.length; i++) {
      buffer = planes[i].getBuffer();
      rowStride = planes[i].getRowStride();
      pixelStride = planes[i].getPixelStride();

      int w = (i == 0) ? width : width / 2;
      int h = (i == 0) ? height : height / 2;
      for (int row = 0; row < h; row++) {
        int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        if (pixelStride == bytesPerPixel) {
          int length = w * bytesPerPixel;
          buffer.get(data, offset, length);

          if (h - row != 1) {
            buffer.position(buffer.position() + rowStride - length);
          }
          offset += length;
        } else {


          if (h - row == 1) {
            buffer.get(rowData, 0, width - pixelStride + 1);
          } else {
            buffer.get(rowData, 0, rowStride);
          }

          for (int col = 0; col < w; col++) {
            data[offset++] = rowData[col * pixelStride];
          }
        }
      }

      if (i == 0 && out != null) {
        System.arraycopy(data, 0, out, 0, out.length);
      }
    }

    Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
    mat.put(0, 0, data);

    return mat;
  }
}
