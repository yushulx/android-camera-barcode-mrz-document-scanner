package expo.modules.dynamsoftbarcodescanner

import android.graphics.Bitmap
import android.util.Log
import com.dynamsoft.cvr.CapturedResult
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.EnumPresetTemplate
import com.dynamsoft.dbr.DecodedBarcodesResult
import com.dynamsoft.license.LicenseManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Dynamsoft Capture Vision native SDK.
 *
 * A single [CaptureVisionRouter] is shared by the live camera scanner and the
 * still-image scanner so that the deep learning models are only loaded once.
 */
class ScannerEngine private constructor() {

  companion object {
    private const val TAG = "ScannerEngine"
    private const val LICENSE_TIMEOUT_SECONDS = 20L

    @Volatile
    private var instance: ScannerEngine? = null

    fun getInstance(): ScannerEngine =
      instance ?: synchronized(this) {
        instance ?: ScannerEngine().also { instance = it }
      }

    /** Template used by both the camera and the file data source. */
    fun template(): String = EnumPresetTemplate.PT_READ_BARCODES
  }

  private val router = CaptureVisionRouter()

  @Volatile
  private var licenseInitialized = false

  fun getRouter(): CaptureVisionRouter = router

  fun isLicenseInitialized(): Boolean = licenseInitialized

  /**
   * Initialize the Dynamsoft license and block until it finishes or times out.
   * @return true when the license is valid
   */
  @Synchronized
  fun initLicense(license: String): Boolean {
    if (licenseInitialized) {
      return true
    }
    val latch = CountDownLatch(1)
    LicenseManager.initLicense(license) { isSuccess, error ->
      licenseInitialized = isSuccess
      if (!isSuccess) {
        Log.e(TAG, "License initialization failed: ${error?.message}")
      }
      latch.countDown()
    }
    try {
      latch.await(LICENSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
    return licenseInitialized
  }

  /** Decode barcodes from a still image, the "file" data source. */
  fun decodeBitmap(bitmap: Bitmap?): DecodedBarcodesResult {
    if (bitmap == null) {
      throw ScannerException("Source bitmap is null")
    }
    return unwrap(router.capture(bitmap, template()))
  }

  /** Decode barcodes from a local file path, the "file" data source. */
  fun decodeFile(path: String): DecodedBarcodesResult {
    if (path.isEmpty()) {
      throw ScannerException("Source path is empty")
    }
    return unwrap(router.capture(path, template()))
  }

  private fun unwrap(result: CapturedResult?): DecodedBarcodesResult {
    return result?.decodedBarcodesResult ?: DecodedBarcodesResult()
  }

  /** Error type raised by the scanner engine. */
  class ScannerException(message: String) : Exception(message)
}