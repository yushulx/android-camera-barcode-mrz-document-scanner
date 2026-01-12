/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.ml

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.opengl.Matrix
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.core.basic_structures.ImageData
import com.dynamsoft.core.basic_structures.EnumImagePixelFormat
import com.dynamsoft.cvr.EnumPresetTemplate
import com.dynamsoft.dbr.DecodedBarcodesResult
import com.dynamsoft.dbr.BarcodeResultItem
import com.google.ar.core.*
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.examples.java.ml.classification.DetectedObjectResult
import com.google.ar.core.examples.java.ml.classification.GoogleCloudVisionDetector
import com.google.ar.core.examples.java.ml.classification.MLKitObjectDetector
import com.google.ar.core.examples.java.ml.classification.ObjectDetector
import com.google.ar.core.examples.java.ml.render.LabelRender
import com.google.ar.core.examples.java.ml.render.PointCloudRender
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*
import android.content.ContentValues
import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * Renders the HelloAR application into using our example Renderer.
 */
class AppRenderer(val activity: MainActivity) : DefaultLifecycleObserver, SampleRender.Renderer, CoroutineScope by MainScope() {
  companion object {
    val TAG = "HelloArRenderer"
  }

  lateinit var view: MainActivityView

  val displayRotationHelper = DisplayRotationHelper(activity)
  lateinit var backgroundRenderer: BackgroundRenderer
  val pointCloudRender = PointCloudRender()
  val labelRenderer = LabelRender()

  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val viewProjectionMatrix = FloatArray(16)

  val arLabeledAnchors = Collections.synchronizedList(mutableListOf<ARLabeledAnchor>())
  var scanButtonWasPressed = false

  val mlKitAnalyzer = MLKitObjectDetector(activity)
  val gcpAnalyzer = GoogleCloudVisionDetector(activity)

  var currentAnalyzer: ObjectDetector = gcpAnalyzer

  var router: CaptureVisionRouter? = null

  val history = Collections.synchronizedMap(HashMap<String, String>())
  var capturePicture = false
  var firstFrameRendered = false

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  fun bindView(view: MainActivityView) {
    // Create an instance of Dynamsoft Capture Vision Router.
    router = CaptureVisionRouter(activity)

    this.view = view

    view.scanButton.setOnClickListener {
      // frame.acquireCameraImage is dependent on an ARCore Frame, which is only available in onDrawFrame.
      // Use a boolean and check its state in onDrawFrame to interact with the camera image.
      scanButtonWasPressed = true
      view.setScanningActive(true)
      hideSnackbar()
    }

    view.historyButton.setOnClickListener() {
      val results = history.values.toTypedArray()

      // Create an AlertDialog builder
      val builder = AlertDialog.Builder(activity)

      // Set the dialog title and items
      builder.setTitle("Barcode History: ${results.size}")
        .setItems(results) { _, which ->
          // 'which' contains the index of the selected item
          Toast.makeText(activity, "You chose ${results[which]}", Toast.LENGTH_SHORT).show()
        }

      // Create and show the dialog
      builder.setPositiveButton("Share") { _, _ ->
        val shareIntent = android.content.Intent().apply {
          action = android.content.Intent.ACTION_SEND
          putExtra(android.content.Intent.EXTRA_TEXT, results.joinToString("\n"))
          type = "text/plain"
        }
        activity.startActivity(android.content.Intent.createChooser(shareIntent, "Share Barcodes"))
      }
      val alertDialog = builder.create()
      val backgroundColor = Color.argb(150, 255, 255, 255)  // 178 is approximately 70% of 255
      alertDialog.window?.setBackgroundDrawable(ColorDrawable(backgroundColor))
      alertDialog.show()
    }

//    view.useCloudMlSwitch.setOnCheckedChangeListener { _, isChecked ->
//      currentAnalyzer = if (isChecked) gcpAnalyzer else mlKitAnalyzer
//    }



    // val gcpConfigured = gcpAnalyzer.credentials != null
    // view.useCloudMlSwitch.isChecked = gcpConfigured
    // view.useCloudMlSwitch.isEnabled = gcpConfigured
    // currentAnalyzer = if (gcpConfigured) gcpAnalyzer else mlKitAnalyzer

    // if (!gcpConfigured) {
    //   showSnackbar("Google Cloud Vision isn't configured (see README). The Cloud ML switch will be disabled.")
    // }

    view.resetButton.setOnClickListener {
      arLabeledAnchors.clear()
      history.clear()
      view.resetButton.isEnabled = false
      hideSnackbar()
    }

    view.saveButton.setOnClickListener {
      capturePicture = true
      Toast.makeText(activity, "Capturing...", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onSurfaceCreated(render: SampleRender) {
    backgroundRenderer = BackgroundRenderer(render).apply {
      setUseDepthVisualization(render, false)
    }
    pointCloudRender.onSurfaceCreated(render)
    labelRenderer.onSurfaceCreated(render)
  }

  override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
  }

  @Volatile
  var objectResults: List<DetectedObjectResult>? = null

  override fun onDrawFrame(render: SampleRender) {
    val session = activity.arCoreSessionHelper.sessionCache ?: return
    session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session)

    val frame = try {
      session.update()
    } catch (e: CameraNotAvailableException) {
      Log.e(TAG, "Camera not available during onDrawFrame", e)
      showSnackbar("Camera not available. Try restarting the app.")
      return
    }

    backgroundRenderer.updateDisplayGeometry(frame)
    backgroundRenderer.drawBackground(render)

    // Hide loading overlay after first successful frame
    if (!firstFrameRendered) {
      firstFrameRendered = true
      view.post { view.hideLoading() }
    }

    // Get camera and projection matrices.
    val camera = frame.camera
    camera.getViewMatrix(viewMatrix, 0)
    camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100.0f)
    Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

    // Handle tracking failures.
    if (camera.trackingState != TrackingState.TRACKING) {
      return
    }

    // Draw point cloud.
    frame.acquirePointCloud().use { pointCloud ->
      pointCloudRender.drawPointCloud(render, pointCloud, viewProjectionMatrix)
    }

    // Frame.acquireCameraImage must be used on the GL thread.
    // Check if the button was pressed last frame to start processing the camera image.
    if (scanButtonWasPressed) {
      scanButtonWasPressed = false
    val cameraImage = frame.tryAcquireCameraImage()
    if (cameraImage != null) {
      // Call our ML model on an IO thread.
      launch(Dispatchers.IO) {
        val cameraId = session.cameraConfig.cameraId
        val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
//          objectResults = currentAnalyzer.analyze(cameraImage, imageRotation)
        if (router != null) {
          var bytes = ByteArray(cameraImage.planes[0].buffer.remaining())
          cameraImage.planes[0].buffer.get(bytes)

          val imageData = ImageData()
          imageData.bytes = bytes
          imageData.width = cameraImage.width
          imageData.height = cameraImage.height
          imageData.stride = cameraImage.planes[0].rowStride
          imageData.format = EnumImagePixelFormat.IPF_GRAYSCALED

          val capturedResult = router!!.capture(imageData, EnumPresetTemplate.PT_READ_BARCODES)
          val decodedBarcodesResult = capturedResult.decodedBarcodesResult

          objectResults = emptyList()
          if (decodedBarcodesResult != null) {
            val items = decodedBarcodesResult.items
            if (items != null && items.isNotEmpty()) {
              val tmp: MutableList<DetectedObjectResult> = mutableListOf()
              for (item in items) {
                val points = item.location.points
                var confidence = 100

                val (x1, y1) = points[0].x to points[0].y
                val (x2, y2) = points[1].x to points[1].y
                val (x3, y3) = points[2].x to points[2].y
                val (x4, y4) = points[3].x to points[3].y
                val centerX = (x1 + x2 + x3 + x4) / 4
                val centerY = (y1 + y2 + y3 + y4) / 4
                val content = item.text
                val label = "●"

                // Calculate barcode size from corner points
                // Use the diagonal of the bounding box as a measure
                val width = kotlin.math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble()).toFloat()
                val height = kotlin.math.sqrt(((x4 - x1) * (x4 - x1) + (y4 - y1) * (y4 - y1)).toDouble()).toFloat()
                val barcodePixelSize = kotlin.math.min(width, height)
                
                // Convert pixel size to approximate meters (assuming typical phone camera FOV)
                // A rough estimate: if barcode is ~10% of image width and ~0.5m away, it's about 5cm
                val imageWidth = cameraImage.width.toFloat()
                val normalizedSize = barcodePixelSize / imageWidth
                // Scale factor: smaller marker relative to barcode (about 30% of barcode size)
                val markerSize = normalizedSize * 0.15f

                val detectedObjectResult = DetectedObjectResult(confidence.toFloat(), label, centerX.toInt() to centerY.toInt(), content, markerSize)
                tmp.add(detectedObjectResult)
              }
              objectResults = tmp
            }
          }
        }

        cameraImage.close()
      }
    }}

    /** If results were completed this frame, create [Anchor]s from model results. */
    val objects = objectResults
    if (objects != null) {
      var hasDuplicate = false
      objectResults = null
//      Log.i(TAG, "$currentAnalyzer got objects: $objects")
      val anchors = objects.mapNotNull { obj ->
        val (atX, atY) = obj.centerCoordinate
        val anchor = createAnchor(atX.toFloat(), atY.toFloat(), frame) ?: return@mapNotNull null
        Log.i(TAG, "Created anchor ${anchor.pose} from hit test")

        if (!history.containsKey(obj.content)) {
          history[obj.content] = obj.content
          ARLabeledAnchor(anchor, obj.label, obj.size)
        }
        else {
          hasDuplicate = true
          return@mapNotNull null
        }
      }
      arLabeledAnchors.addAll(anchors)
      view.post {
        view.resetButton.isEnabled = arLabeledAnchors.isNotEmpty()
        view.setScanningActive(false)
        when {
//          objects.isEmpty() && currentAnalyzer == mlKitAnalyzer && !mlKitAnalyzer.hasCustomModel() ->
//            showSnackbar("Default ML Kit classification model returned no results. " +
//              "For better classification performance, see the README to configure a custom model.")
          objects.isEmpty() ->
            showSnackbar("No barcode found")
          anchors.size != objects.size && !hasDuplicate ->
            showSnackbar("Objects were classified, but could not be attached to an anchor. " +
              "Try moving your device around to obtain a better understanding of the environment.")
        }
      }
    }

    // Draw labels at their anchor position.
    for (arDetectedObject in arLabeledAnchors) {
      val anchor = arDetectedObject.anchor
      if (anchor.trackingState != TrackingState.TRACKING) continue
      labelRenderer.draw(
        render,
        viewProjectionMatrix,
        anchor.pose,
        camera.pose,
        arDetectedObject.label,
        arDetectedObject.size
      )
    }

    if (capturePicture) {
      capturePicture = false
      saveBitmapFromGLSurface(render)
    }
  }

  /**
   * Utility method for [Frame.acquireCameraImage] that maps [NotYetAvailableException] to `null`.
   */
  fun Frame.tryAcquireCameraImage() = try {
    acquireCameraImage()
  } catch (e: NotYetAvailableException) {
    null
  } catch (e: Throwable) {
    throw e
  }

  private fun showSnackbar(message: String): Unit =
    activity.view.snackbarHelper.showError(activity, message)

  private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)

  /**
   * Temporary arrays to prevent allocations in [createAnchor].
   */
  private val convertFloats = FloatArray(4)
  private val convertFloatsOut = FloatArray(4)

  /** Create an anchor using (x, y) coordinates in the [Coordinates2d.IMAGE_PIXELS] coordinate space. */
  fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
    // IMAGE_PIXELS -> VIEW
    convertFloats[0] = xImage
    convertFloats[1] = yImage
    frame.transformCoordinates2d(
      Coordinates2d.IMAGE_PIXELS,
      convertFloats,
      Coordinates2d.VIEW,
      convertFloatsOut
    )

    // Conduct a hit test using the VIEW coordinates
    val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
    
    // Prioritize Plane hits for stability
    val planeHit = hits.find { it.trackable is Plane }
    if (planeHit != null) {
      return planeHit.trackable.createAnchor(planeHit.hitPose)
    }

    // Secondary choice: Point
    val pointHit = hits.find { it.trackable is Point }
    if (pointHit != null) {
      return pointHit.trackable.createAnchor(pointHit.hitPose)
    }

    // Fallback: Create anchor at fixed distance (e.g. 0.5m) relative to camera
    // This allows placement even if no plane is detected yet.
    val cameraPose = frame.camera.pose
    // Place 50cm in front of camera
    val newPose = cameraPose.compose(Pose.makeTranslation(0f, 0f, -0.5f))
    return activity.arCoreSessionHelper.sessionCache?.createAnchor(newPose)
  }

  private fun saveBitmapFromGLSurface(render: SampleRender) {
    val width = view.surfaceView.width
    val height = view.surfaceView.height
    val buffer = ByteBuffer.allocateDirect(width * height * 4)
    buffer.order(ByteOrder.nativeOrder())
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

    launch(Dispatchers.IO) {
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      buffer.rewind()
      bitmap.copyPixelsFromBuffer(buffer)
      
      // Flip vertical because GL is bottom-left origin
      val matrix = android.graphics.Matrix()
      matrix.preScale(1.0f, -1.0f)
      val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)

      saveBitmapToGallery(flippedBitmap)
    }
  }

  private fun saveBitmapToGallery(bitmap: Bitmap) {
    val filename = "AR_Scan_${System.currentTimeMillis()}.jpg"
    var fos: java.io.OutputStream? = null
    var uri: android.net.Uri? = null
    val contentValues = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
      put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
      }
    }

    val resolver = activity.contentResolver
    try {
      uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
      if (uri != null) {
        fos = resolver.openOutputStream(uri)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos!!)
        fos?.close()
        launch(Dispatchers.Main) {
           Toast.makeText(activity, "Saved to Gallery: $filename", Toast.LENGTH_SHORT).show()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save image", e)
      launch(Dispatchers.Main) {
        showSnackbar("Failed to save image: ${e.message}")
      }
    }
  }
}

data class ARLabeledAnchor(val anchor: Anchor, val label: String, val size: Float = 0.05f)