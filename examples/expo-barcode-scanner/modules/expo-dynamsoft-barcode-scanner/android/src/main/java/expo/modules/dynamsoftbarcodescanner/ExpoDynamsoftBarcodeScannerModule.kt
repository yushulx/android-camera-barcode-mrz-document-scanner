package expo.modules.dynamsoftbarcodescanner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * In-app Expo module that exposes the Dynamsoft Capture Vision **native** Android
 * SDK to the React Native layer.
 *
 * Methods:
 * - `initLicense` - activate the SDK
 * - `startScan` - live camera data source (full screen native scanner)
 * - `scanFromGallery` - still image data source via the system photo picker
 * - `scanFile` - still image data source from a known path or content URI
 *
 * Activity results are handled by [BridgeResultActivity]: Expo modules cannot
 * register React Native activity-result listeners, so the module starts that
 * invisible Activity which runs `startActivityForResult` and settles the
 * promise parked in its static [BridgeResultActivity.pending] slot.
 */
class ExpoDynamsoftBarcodeScannerModule : Module() {

  companion object {
    private const val TAG = "ExpoBarcodeScanner"
  }

  /** Simple worker pool used for off-main-thread image decoding. */
  private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  override fun definition() = ModuleDefinition {
    Name("ExpoDynamsoftBarcodeScanner")

    AsyncFunction("initLicense") { license: String?, promise: Promise ->
      val key = license?.takeIf { it.isNotEmpty() } ?: License.KEY
      val ok = ScannerEngine.getInstance().initLicense(key)
      promise.resolve(
        mapOf(
          "success" to ok,
          "message" to if (ok) "License activated" else "License initialization failed"
        )
      )
    }

    AsyncFunction("startScan") { promise: Promise ->
      if (!ensureLicense(promise)) return@AsyncFunction
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No foreground activity is available", null)
        return@AsyncFunction
      }
      BridgeResultActivity.pending =
        Pending(promise, BridgeResultActivity.KIND_SCAN, activity.applicationContext)
      activity.startActivity(
        Intent(activity, BridgeResultActivity::class.java)
          .putExtra(BridgeResultActivity.EXTRA_KIND, BridgeResultActivity.KIND_SCAN)
      )
    }

    AsyncFunction("scanFromGallery") { promise: Promise ->
      if (!ensureLicense(promise)) return@AsyncFunction
      val activity = appContext.currentActivity
      if (activity == null) {
        promise.reject("NO_ACTIVITY", "No foreground activity is available", null)
        return@AsyncFunction
      }
      BridgeResultActivity.pending =
        Pending(promise, BridgeResultActivity.KIND_GALLERY, activity.applicationContext)
      activity.startActivity(
        Intent(activity, BridgeResultActivity::class.java)
          .putExtra(BridgeResultActivity.EXTRA_KIND, BridgeResultActivity.KIND_GALLERY)
      )
    }

    AsyncFunction("scanFile") { uri: String, promise: Promise ->
      if (uri.isEmpty()) {
        promise.reject("MISSING_URI", "Missing 'uri' option", null)
        return@AsyncFunction
      }
      if (!ensureLicense(promise)) return@AsyncFunction
      val context = appContext.currentActivity?.applicationContext
      if (context == null) {
        promise.reject("NO_CONTEXT", "No application context is available", null)
        return@AsyncFunction
      }
      backgroundExecutor.execute {
        try {
          val decoded = decodeUri(uri, context)
          promise.resolve(
            mapOf("results" to BarcodeResults.fromJson(BarcodeResults.toJsonArray(decoded.items).toString()))
          )
        } catch (e: Exception) {
          Log.e(TAG, "Failed to decode image", e)
          promise.reject("DECODE_FAILED", "Failed to decode image: ${e.message}", e)
        }
      }
    }
    OnDestroy {
      backgroundExecutor.shutdown()
    }
  }

  private fun ensureLicense(promise: Promise): Boolean {
    val engine = ScannerEngine.getInstance()
    if (engine.isLicenseInitialized()) return true
    if (!engine.initLicense(License.KEY)) {
      promise.reject(
        "LICENSE_FAILED",
        "Dynamsoft license initialization failed. Check your license key.",
        null
      )
      return false
    }
    return true
  }

  /** Decode a content URI, a `file://` URI, or a raw absolute path. */
  private fun decodeUri(uri: String, context: android.content.Context): com.dynamsoft.dbr.DecodedBarcodesResult {
    val engine = ScannerEngine.getInstance()
    return if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
      engine.decodeBitmap(loadBitmap(Uri.parse(uri), context))
    } else {
      val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
      engine.decodeFile(path)
    }
  }

  private fun loadBitmap(uri: Uri, context: android.content.Context): Bitmap {
    val stream: InputStream = context.contentResolver.openInputStream(uri)
      ?: throw ScannerEngine.ScannerException("Cannot open $uri")
    stream.use { return BitmapFactory.decodeStream(it) }
  }
}
