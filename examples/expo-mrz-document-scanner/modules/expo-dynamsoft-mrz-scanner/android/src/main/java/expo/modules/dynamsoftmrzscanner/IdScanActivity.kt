package expo.modules.dynamsoftmrzscanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dynamsoft.core.basic_structures.CompletionListener
import com.dynamsoft.core.basic_structures.EnumColourChannelUsageType
import com.dynamsoft.core.basic_structures.Quadrilateral
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo
import com.dynamsoft.core.intermediate_results.ScaledColourImageUnit
import com.dynamsoft.cvr.CapturedResult
import com.dynamsoft.cvr.CapturedResultReceiver
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.CaptureVisionRouterException
import com.dynamsoft.cvr.intermediate_results.IntermediateResultManager
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver
import com.dynamsoft.dce.CameraEnhancer
import com.dynamsoft.dce.CameraEnhancerException
import com.dynamsoft.dce.CameraView
import com.dynamsoft.dce.DrawingItem
import com.dynamsoft.dce.DrawingLayer
import com.dynamsoft.dce.EnumEnhancerFeatures
import com.dynamsoft.dce.QuadDrawingItem
import com.dynamsoft.dce.utils.PermissionUtil
import com.dynamsoft.dcp.ParsedResult
import com.dynamsoft.dcp.ParsedResultItem
import com.dynamsoft.ddn.ProcessedDocumentResult
import com.dynamsoft.ddn.intermediate_results.DeskewedImageUnit
import com.dynamsoft.ddn.intermediate_results.DetectedQuadsUnit
import com.dynamsoft.diu.IdentityProcessor
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLinesUnit
import com.dynamsoft.dlr.intermediate_results.RecognizedTextLinesUnit
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Full-screen native camera scanner for identity documents.
 *
 * Live preview and processing are provided by the Dynamsoft MRZ Scanner native SDK
 * (CameraX + Capture Vision). When the MRZ zone and the portrait are detected the
 * capture button becomes enabled; tapping it returns the parsed fields and the
 * cropped portrait photo to the React Native layer.
 */
class IdScanActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "IdScanActivity"
  }

  private var camera: CameraEnhancer? = null
  private lateinit var cameraView: CameraView
  private var router: CaptureVisionRouter? = null
  private var irm: IntermediateResultManager? = null
  private var intermediateReceiver: IntermediateResultReceiver? = null
  private var resultReceiver: CapturedResultReceiver? = null
  private val idProcessor = IdentityProcessor()

  private lateinit var statusView: TextView
  private lateinit var captureButton: Button

  private var scaledColourImageUnit: ScaledColourImageUnit? = null
  private var localizedTextLinesUnit: LocalizedTextLinesUnit? = null
  private var recognizedTextLinesUnit: RecognizedTextLinesUnit? = null
  private var detectedQuadsUnit: DetectedQuadsUnit? = null
  private var deskewedImageUnit: DeskewedImageUnit? = null

  private var pendingFields: Map<String, String>? = null
  private var pendingPortrait: Bitmap? = null
  private var pendingDocument: Bitmap? = null

  private var confirmed = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_id_scan)

    cameraView = findViewById(R.id.camera_view)
    statusView = findViewById(R.id.status_text)
    captureButton = findViewById(R.id.btn_capture)
    val cancelButton: Button = findViewById(R.id.btn_cancel)

    cancelButton.setOnClickListener { finishCanceled() }
    captureButton.setOnClickListener { confirm() }

    val engine = IdScannerEngine.getInstance()
    if (!engine.isLicenseInitialized() && !engine.initLicense(License.KEY)) {
      setResult(RESULT_CANCELED, IdScanResultContract.error("Dynamsoft license initialization failed. Check your license key."))
      finish()
      return
    }
    try {
      engine.ensureTemplates()
    } catch (e: IdScannerEngine.ScannerException) {
      Log.e(TAG, "Template loading failed", e)
      setResult(RESULT_CANCELED, IdScanResultContract.error(e.message ?: "Template loading failed"))
      finish()
      return
    }

    PermissionUtil.requestCameraPermission(this)

    val activeRouter = engine.getRouter()
    val activeCamera = CameraEnhancer(cameraView, this)
    router = activeRouter
    camera = activeCamera

    activeCamera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL)
    try {
      activeCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER)
    } catch (e: CameraEnhancerException) {
      // Frame filter is an optional quality improvement.
    }

    try {
      activeRouter.setInput(activeCamera)
    } catch (e: CaptureVisionRouterException) {
      Log.e(TAG, "Failed to bind camera to Capture Vision", e)
      setResult(RESULT_CANCELED, IdScanResultContract.error("Failed to bind camera: ${e.message}"))
      finish()
      return
    }

    intermediateReceiver = object : IntermediateResultReceiver {
      override fun onDetectedQuadsReceived(u: DetectedQuadsUnit, i: IntermediateResultExtraInfo) {
        detectedQuadsUnit = u
      }

      override fun onLocalizedTextLinesReceived(u: LocalizedTextLinesUnit, i: IntermediateResultExtraInfo) {
        localizedTextLinesUnit = u
      }

      override fun onRecognizedTextLinesReceived(u: RecognizedTextLinesUnit, i: IntermediateResultExtraInfo) {
        recognizedTextLinesUnit = u
      }

      override fun onDeskewedImageReceived(u: DeskewedImageUnit, i: IntermediateResultExtraInfo) {
        deskewedImageUnit = u
      }

      override fun onScaledColourImageUnitReceived(u: ScaledColourImageUnit, i: IntermediateResultExtraInfo) {
        scaledColourImageUnit = u
      }
    }

    resultReceiver = object : CapturedResultReceiver {
      override fun onCapturedResultReceived(result: CapturedResult) {
        val pr: ParsedResult? = result.parsedResult
        if (pr == null || pr.items == null || pr.items.isEmpty()) {
          return
        }
        val item: ParsedResultItem = pr.items[0]
        val fields: Map<String, String> = IdResultFormatter.toFieldMap(item)
        if (fields.isEmpty()) {
          return
        }

        // Locate the portrait photo from the auxiliary region of the MRZ zone.
        var portraitZone: Quadrilateral? = null
        val localized = localizedTextLinesUnit
        if (localized != null && localized.auxiliaryRegionElementsCount > 0) {
          var highConfidence = false
          for (index in 0 until localized.auxiliaryRegionElementsCount) {
            val element = localized.getAuxiliaryRegionElement(index)
            if ("PortraitZone" == element.name && element.confidence > 60) {
              highConfidence = true
              break
            }
          }
          val quads = detectedQuadsUnit
          if (highConfidence && quads != null && quads.count > 0) {
            portraitZone = idProcessor.findPortraitZone(
              scaledColourImageUnit,
              localized,
              recognizedTextLinesUnit,
              quads,
              deskewedImageUnit
            )
          }
        }

        // Keep the zone only when it sits inside the detected document.
        val documentResult: ProcessedDocumentResult? = result.processedDocumentResult
        if (portraitZone != null && documentResult != null &&
          documentResult.detectedQuadResultItems != null &&
          documentResult.detectedQuadResultItems.isNotEmpty()
        ) {
          val docRegion: Quadrilateral = documentResult.detectedQuadResultItems[0].location
          val valid = inside(docRegion, portraitZone!!) && docRegion.area / portraitZone!!.area >= 3
          if (!valid) {
            portraitZone = null
          }
        }

        var portrait: Bitmap? = null
        val zone = portraitZone
        val scaled = scaledColourImageUnit
        if (zone != null && scaled != null) {
          try {
            val scaledBitmap: Bitmap? = scaled.imageData?.toBitmap()
            if (scaledBitmap != null) {
              portrait = deskewPortrait(scaledBitmap, zone)
            }
          } catch (e: Exception) {
            Log.e(TAG, "Portrait crop failed", e)
          }
        }

        // The document-detection step of ReadPassportAndId also emits a
        // deskewed page image (the same pipeline the standalone document
        // scanner uses), so we surface it as a preview of the detected
        // card boundary + perspective correction.
        var documentImage: Bitmap? = null
        if (documentResult != null && documentResult.deskewedImageResultItems != null &&
          documentResult.deskewedImageResultItems.isNotEmpty()
        ) {
          try {
            val deskewedItem = documentResult.deskewedImageResultItems[0]
            val imageData = deskewedItem.imageData
            if (imageData != null) {
              documentImage = imageData.toBitmap()
            }
          } catch (e: Exception) {
            Log.e(TAG, "Deskewed document extraction failed", e)
          }
        }

        val portraitBitmap = portrait
        val documentBitmap = documentImage
        runOnUiThread {
          pendingFields = fields
          pendingPortrait = portraitBitmap
          pendingDocument = documentBitmap
          captureButton.isEnabled = true
          statusView.text = if (zone != null) "Portrait found - ready" else "MRZ ready"
          if (zone != null) {
            drawFaceOverlay(zone)
          }
        }
      }
    }

    irm = activeRouter.intermediateResultManager
    intermediateReceiver?.let { irm?.addResultReceiver(it) }
    resultReceiver?.let { activeRouter.addResultReceiver(it) }
  }

  override fun onResume() {
    super.onResume()
    pendingFields = null
    pendingPortrait = null
    pendingDocument = null
    captureButton.isEnabled = false
    statusView.setText(R.string.status_initializing)
    val activeCamera = camera ?: return
    val activeRouter = router ?: return
    activeCamera.open()
    activeRouter.startCapturing(IdScannerEngine.template(), object : CompletionListener {
      override fun onSuccess() {
        runOnUiThread { statusView.setText(R.string.status_scanning) }
      }

      override fun onFailure(errorCode: Int, errorString: String) {
        runOnUiThread { statusView.text = errorString }
      }
    })
  }

  override fun onPause() {
    super.onPause()
    router?.stopCapturing()
    camera?.close()
  }

  override fun onDestroy() {
    super.onDestroy()
    intermediateReceiver?.let { irm?.removeResultReceiver(it) }
    resultReceiver?.let { router?.removeResultReceiver(it) }
  }

  // NOTE: with no onBackPressed override the system back button finishes the
  // Activity with RESULT_CANCELED and the module rejects the promise as
  // "canceled", matching the explicit Cancel button behaviour.

  private fun drawFaceOverlay(quad: Quadrilateral) {
    val layer = cameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID) ?: return
    val items = ArrayList<DrawingItem<*>>()
    items.add(QuadDrawingItem(quad))
    layer.setDrawingItems(items)
  }

  private fun confirm() {
    if (confirmed || pendingFields == null) {
      return
    }
    confirmed = true
    router?.stopCapturing()
    try {
      val json = JSONObject()
      json.put("fields", JSONObject(pendingFields))
      pendingPortrait?.let { json.put("portraitBase64", toBase64(it)) }
      pendingDocument?.let { json.put("documentImageBase64", toBase64(it)) }
      setResult(RESULT_OK, IdScanResultContract.ok(json.toString()))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to serialize result", e)
      setResult(RESULT_CANCELED, IdScanResultContract.error("Failed to serialize result: ${e.message}"))
    }
    finish()
  }

  private fun finishCanceled() {
    if (confirmed) {
      return
    }
    setResult(RESULT_CANCELED, IdScanResultContract.error("canceled"))
    finish()
  }

  private fun toBase64(bitmap: Bitmap): String {
    val os = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)
    val bytes = os.toByteArray()
    return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  private fun inside(outer: Quadrilateral, inner: Quadrilateral): Boolean {
    val pts: Array<Point>? = inner.points
    if (pts == null) {
      return false
    }
    for (p in pts) {
      if (!outer.isPointInQuadrilateral(p)) {
        return false
      }
    }
    return true
  }

  private fun deskewPortrait(src: Bitmap, quad: Quadrilateral): Bitmap? {
    val pts: Array<Point>? = quad.points
    if (pts == null || pts.size < 4) {
      return null
    }
    val srcPts = floatArrayOf(
      pts[0].x.toFloat(), pts[0].y.toFloat(),
      pts[1].x.toFloat(), pts[1].y.toFloat(),
      pts[2].x.toFloat(), pts[2].y.toFloat(),
      pts[3].x.toFloat(), pts[3].y.toFloat()
    )
    val w = maxOf(
      Math.hypot((pts[1].x - pts[0].x).toDouble(), (pts[1].y - pts[0].y).toDouble()).toFloat(),
      Math.hypot((pts[2].x - pts[3].x).toDouble(), (pts[2].y - pts[3].y).toDouble()).toFloat()
    )
    val h = maxOf(
      Math.hypot((pts[3].x - pts[0].x).toDouble(), (pts[3].y - pts[0].y).toDouble()).toFloat(),
      Math.hypot((pts[2].x - pts[1].x).toDouble(), (pts[2].y - pts[1].y).toDouble()).toFloat()
    )
    if (w <= 0 || h <= 0) {
      return null
    }
    val iw = Math.round(w)
    val ih = Math.round(h)
    val dstPts = floatArrayOf(0f, 0f, iw.toFloat(), 0f, iw.toFloat(), ih.toFloat(), 0f, ih.toFloat())
    val matrix = Matrix()
    if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
      return null
    }
    val result = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
    Canvas(result).drawBitmap(src, matrix, null)
    return result
  }
}
