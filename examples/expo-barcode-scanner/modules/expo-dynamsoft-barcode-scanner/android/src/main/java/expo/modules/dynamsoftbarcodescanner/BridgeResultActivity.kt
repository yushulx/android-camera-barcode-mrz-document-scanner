package expo.modules.dynamsoftbarcodescanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import expo.modules.kotlin.Promise
import java.io.InputStream

/** A promise parked by [ExpoDynamsoftBarcodeScannerModule] while the bridge is open. */
data class Pending(
  val promise: Promise,
  val kind: String,
  val applicationContext: Context
)

/**
 * Invisible host for `startActivityForResult` calls that the Expo module must
 * make (the full-screen scanner, the system photo picker). Expo modules cannot
 * register React Native activity-result listeners, so the module launches this
 * transparent Activity instead; it launches the real target, receives the
 * outcome in its own `onActivityResult` and resolves/rejects the promise the
 * module parked in the [BridgeResultActivity.pending] slot.
 */
class BridgeResultActivity : Activity() {

  companion object {
    private const val TAG = "BridgeResultActivity"
    private const val REQUEST = 1

    const val EXTRA_KIND = "expo.modules.dynamsoftbarcodescanner.BRIDGE_KIND"
    const val KIND_SCAN = "scan"
    const val KIND_GALLERY = "gallery"

    /** Single-flight slot shared with [ExpoDynamsoftBarcodeScannerModule]. */
    @Volatile
    var pending: Pending? = null
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    when (intent.getStringExtra(EXTRA_KIND)) {
      KIND_SCAN -> {
        startActivityForResult(Intent(this, ScannerActivity::class.java), REQUEST)
      }
      KIND_GALLERY -> {
        val picker = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
          type = "image/*"
        }
        if (picker.resolveActivity(packageManager) == null) {
          finishWithError("No photo picker available on this device")
          return
        }
        startActivityForResult(Intent.createChooser(picker, "Select an image"), REQUEST)
      }
      else -> finishWithError("Unknown bridge kind")
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    val holder = pending
    pending = null
    if (resultCode != RESULT_OK) {
      val error = data?.getStringExtra(ScanResultContract.EXTRA_ERROR)
      holder?.promise?.reject("canceled", error ?: "canceled", null)
      finish()
      return
    }
    when (holder?.kind) {
      KIND_SCAN -> {
        val json = data?.getStringExtra(ScanResultContract.EXTRA_RESULTS)
        if (json == null) {
          holder.promise.reject("canceled", "Scan failed or was canceled", null)
        } else {
          try {
            holder.promise.resolve(mapOf("results" to BarcodeResults.fromJson(json)))
          } catch (e: Exception) {
            holder.promise.reject("INVALID_RESULT", "Invalid scan result: ${e.message}", e)
          }
        }
      }
      KIND_GALLERY -> {
        val uri = data?.data
        if (uri == null) {
          holder.promise.reject("canceled", "No image selected", null)
        } else {
          Thread {
            try {
              val engine = ScannerEngine.getInstance()
              val items = engine.decodeBitmap(loadBitmap(uri, holder.applicationContext)).items
              holder.promise.resolve(
                mapOf("results" to BarcodeResults.fromJson(BarcodeResults.toJsonArray(items).toString()))
              )
            } catch (e: Exception) {
              Log.e(TAG, "Failed to decode picked image", e)
              holder.promise.reject("DECODE_FAILED", "Failed to decode image: ${e.message}", e)
            }
          }.start()
        }
      }
      else -> holder?.promise?.reject("BRIDGE_ERROR", "Unknown bridge kind", null)
    }
    finish()
  }

  private fun finishWithError(message: String) {
    val holder = pending
    pending = null
    holder?.promise?.reject("BRIDGE_ERROR", message, null)
    finish()
  }

  private fun loadBitmap(uri: Uri, context: Context): android.graphics.Bitmap {
    val stream: InputStream = context.contentResolver.openInputStream(uri)
      ?: throw ScannerEngine.ScannerException("Cannot open $uri")
    stream.use { return BitmapFactory.decodeStream(it) }
  }
}
