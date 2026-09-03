package expo.modules.dynamsoftbarcodescanner

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dynamsoft.core.basic_structures.CompletionListener
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.CaptureVisionRouterException
import com.dynamsoft.cvr.CapturedResultReceiver
import com.dynamsoft.dbr.BarcodeResultItem
import com.dynamsoft.dbr.DecodedBarcodesResult
import com.dynamsoft.dce.CameraEnhancer
import com.dynamsoft.dce.CameraView
import com.dynamsoft.dce.DrawingItem
import com.dynamsoft.dce.DrawingLayer
import com.dynamsoft.dce.QuadDrawingItem
import com.dynamsoft.dce.utils.PermissionUtil

/**
 * Full-screen native camera scanner.
 *
 * The preview comes from the Dynamsoft Camera Enhancer (CameraX under the hood)
 * and every frame is processed by the Dynamsoft Capture Vision native SDK.
 * Nothing here touches the React Native view hierarchy or the JS SDK.
 */
class ScannerActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "ScannerActivity"
  }

  private lateinit var camera: CameraEnhancer
  private lateinit var cameraView: CameraView
  private lateinit var router: CaptureVisionRouter
  private var resultReceiver: CapturedResultReceiver? = null

  private lateinit var statusView: TextView
  private lateinit var captureButton: Button

  private var latestItems: Array<BarcodeResultItem> = emptyArray()
  private var confirmed = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_scanner)

    cameraView = findViewById(R.id.camera_view)
    statusView = findViewById(R.id.status_text)
    captureButton = findViewById(R.id.btn_capture)
    val cancelButton: Button = findViewById(R.id.btn_cancel)

    cancelButton.setOnClickListener { finishCanceled() }
    captureButton.setOnClickListener { confirm() }

    val engine = ScannerEngine.getInstance()
    if (!engine.isLicenseInitialized() && !engine.initLicense(License.KEY)) {
      setResult(RESULT_CANCELED, ScanResultContract.error("Dynamsoft license initialization failed. Check your license key."))
      finish()
      return
    }

    PermissionUtil.requestCameraPermission(this)

    router = engine.getRouter()
    camera = CameraEnhancer(cameraView, this)
    try {
      router.setInput(camera)
    } catch (e: CaptureVisionRouterException) {
      Log.e(TAG, "Failed to bind camera to Capture Vision", e)
      setResult(RESULT_CANCELED, ScanResultContract.error("Failed to bind camera: ${e.message}"))
      finish()
      return
    }

    resultReceiver = object : CapturedResultReceiver {
      override fun onDecodedBarcodesReceived(result: DecodedBarcodesResult) {
        latestItems = result.items ?: emptyArray()
        runOnUiThread { refreshUi() }
      }
    }
    router.addResultReceiver(resultReceiver)
  }

  override fun onResume() {
    super.onResume()
    latestItems = emptyArray()
    confirmed = false
    try {
      camera.open()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to open camera", e)
    }
    router.startCapturing(ScannerEngine.template(), object : CompletionListener {
      override fun onSuccess() {
        runOnUiThread { statusView.text = getString(R.string.status_scanning) }
      }

      override fun onFailure(errorCode: Int, errorString: String) {
        runOnUiThread { statusView.text = errorString }
      }
    })
  }

  override fun onPause() {
    super.onPause()
    router.stopCapturing()
    camera.close()
  }

  override fun onDestroy() {
    super.onDestroy()
    router.removeResultReceiver(resultReceiver)
  }

  private fun refreshUi() {
    val count = latestItems.size
    if (count == 0) {
      statusView.text = getString(R.string.status_scanning)
      captureButton.isEnabled = false
      clearOverlay()
      return
    }
    statusView.text = resources.getQuantityString(R.plurals.barcodes_found, count, count)
    captureButton.isEnabled = true
    drawOverlay()
  }

  private fun drawOverlay() {
    val layer = cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID) ?: return
    val items = ArrayList<DrawingItem<*>>()
    for (item in latestItems) {
      item.location?.let { items.add(QuadDrawingItem(it)) }
    }
    layer.setDrawingItems(items)
  }

  private fun clearOverlay() {
    cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID)?.clearDrawingItems()
  }

  private fun confirm() {
    if (confirmed) {
      return
    }
    confirmed = true
    router.stopCapturing()
    try {
      val array = BarcodeResults.toJsonArray(latestItems)
      setResult(RESULT_OK, ScanResultContract.ok(array.toString()))
    } catch (e: Exception) {
      setResult(RESULT_CANCELED, ScanResultContract.error("Failed to serialize results: ${e.message}"))
    }
    finish()
  }

  private fun finishCanceled() {
    setResult(RESULT_CANCELED, ScanResultContract.error("canceled"))
    finish()
  }

  // NOTE: with no onBackPressed override the system back button finishes the
  // Activity with RESULT_CANCELED and the module rejects the promise as
  // "canceled", matching the explicit Cancel button behaviour.
}