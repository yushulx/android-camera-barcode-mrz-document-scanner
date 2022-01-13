package com.example.qrcodescanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Environment;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.dynamsoft.dbr.Point;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
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

  public static Point rotateCW90(Point point, int width) {
    Point rotatedPoint = new Point();
    rotatedPoint.x = width - point.y;
    rotatedPoint.y = point.x;
    return rotatedPoint;
  }

  public static Point rotateCCW90(Point point, int width) {
    Point rotatedPoint = new Point();
    rotatedPoint.x = point.y;
    rotatedPoint.y = width - point.x;
    return rotatedPoint;
  }

  public static Bitmap getBitmap(ImageProxy image) {
    FrameMetadata frameMetadata =
            new FrameMetadata.Builder()
                    .setWidth(image.getWidth())
                    .setHeight(image.getHeight())
                    .setRotation(image.getImageInfo().getRotationDegrees())
                    .build();

    ByteBuffer nv21Buffer =
            yuv420ThreePlanesToNV21(image.getPlanes(), image.getWidth(), image.getHeight());
    return getBitmap(nv21Buffer, frameMetadata);
  }

  private static ByteBuffer yuv420ThreePlanesToNV21(
          ImageProxy.PlaneProxy[] yuv420888planes, int width, int height) {
    int imageSize = width * height;
    byte[] out = new byte[imageSize + 2 * (imageSize / 4)];

    if (areUVPlanesNV21(yuv420888planes, width, height)) {
      // Copy the Y values.
      yuv420888planes[0].getBuffer().get(out, 0, imageSize);

      ByteBuffer uBuffer = yuv420888planes[1].getBuffer();
      ByteBuffer vBuffer = yuv420888planes[2].getBuffer();
      // Get the first V value from the V buffer, since the U buffer does not contain it.
      vBuffer.get(out, imageSize, 1);
      // Copy the first U value and the remaining VU values from the U buffer.
      uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1);
    } else {
      // Fallback to copying the UV values one by one, which is slower but also works.
      // Unpack Y.
      unpackPlane(yuv420888planes[0], width, height, out, 0, 1);
      // Unpack U.
      unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2);
      // Unpack V.
      unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2);
    }

    return ByteBuffer.wrap(out);
  }

  private static boolean areUVPlanesNV21(ImageProxy.PlaneProxy[] planes, int width, int height) {
    int imageSize = width * height;

    ByteBuffer uBuffer = planes[1].getBuffer();
    ByteBuffer vBuffer = planes[2].getBuffer();

    // Backup buffer properties.
    int vBufferPosition = vBuffer.position();
    int uBufferLimit = uBuffer.limit();

    // Advance the V buffer by 1 byte, since the U buffer will not contain the first V value.
    vBuffer.position(vBufferPosition + 1);
    // Chop off the last byte of the U buffer, since the V buffer will not contain the last U value.
    uBuffer.limit(uBufferLimit - 1);

    // Check that the buffers are equal and have the expected number of elements.
    boolean areNV21 =
            (vBuffer.remaining() == (2 * imageSize / 4 - 2)) && (vBuffer.compareTo(uBuffer) == 0);

    // Restore buffers to their initial state.
    vBuffer.position(vBufferPosition);
    uBuffer.limit(uBufferLimit);

    return areNV21;
  }

  private static void unpackPlane(
          ImageProxy.PlaneProxy plane, int width, int height, byte[] out, int offset, int pixelStride) {
    ByteBuffer buffer = plane.getBuffer();
    buffer.rewind();

    // Compute the size of the current plane.
    // We assume that it has the aspect ratio as the original image.
    int numRow = (buffer.limit() + plane.getRowStride() - 1) / plane.getRowStride();
    if (numRow == 0) {
      return;
    }
    int scaleFactor = height / numRow;
    int numCol = width / scaleFactor;

    // Extract the data in the output buffer.
    int outputPos = offset;
    int rowStart = 0;
    for (int row = 0; row < numRow; row++) {
      int inputPos = rowStart;
      for (int col = 0; col < numCol; col++) {
        out[outputPos] = buffer.get(inputPos);
        outputPos += pixelStride;
        inputPos += plane.getPixelStride();
      }
      rowStart += plane.getRowStride();
    }
  }

  public static Bitmap getBitmap(ByteBuffer data, FrameMetadata metadata) {
    data.rewind();
    byte[] imageInBuffer = new byte[data.limit()];
    data.get(imageInBuffer, 0, imageInBuffer.length);
    try {
      YuvImage image =
              new YuvImage(
                      imageInBuffer, ImageFormat.NV21, metadata.getWidth(), metadata.getHeight(), null);
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      image.compressToJpeg(new Rect(0, 0, metadata.getWidth(), metadata.getHeight()), 80, stream);

      Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

      stream.close();
      return rotateBitmap(bmp, metadata.getRotation(), false, false);
    } catch (Exception e) {
      Log.e("VisionProcessorBase", "Error: " + e.getMessage());
    }
    return null;
  }

  private static Bitmap rotateBitmap(
          Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
    Matrix matrix = new Matrix();

    // Rotate the image back to straight.
    matrix.postRotate(rotationDegrees);

    // Mirror the image along the X or Y axis.
    matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
    Bitmap rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

    // Recycle the old bitmap if it has changed.
    if (rotatedBitmap != bitmap) {
      bitmap.recycle();
    }
    return rotatedBitmap;
  }
}
