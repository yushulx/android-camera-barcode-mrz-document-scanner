package expo.modules.dynamsoftmrzscanner

import android.graphics.Bitmap
import android.util.Log
import com.dynamsoft.cvr.CapturedResult
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.CaptureVisionRouterException
import com.dynamsoft.dcp.ParsedResult
import com.dynamsoft.license.LicenseManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Dynamsoft MRZ Scanner native SDK.
 *
 * A single [CaptureVisionRouter] is shared by the live camera scanner and the
 * still-image scanner so that the deep learning models are only loaded once.
 */
class IdScannerEngine private constructor() {

  companion object {
    private const val TAG = "IdScannerEngine"
    private const val LICENSE_TIMEOUT_SECONDS = 20L

    @Volatile
    private var instance: IdScannerEngine? = null

    fun getInstance(): IdScannerEngine =
      instance ?: synchronized(this) {
        instance ?: IdScannerEngine().also { instance = it }
      }

    /** Name of the built-in MRZ template shipped with the mrzscannerbundle. */
    fun template(): String = "ReadPassportAndId"
  }

  private val router = CaptureVisionRouter()

  @Volatile
  private var licenseInitialized = false
  private var templateReady = false

  fun getRouter(): CaptureVisionRouter = router

  fun isLicenseInitialized(): Boolean = licenseInitialized

  /**
   * Initialize the Dynamsoft license and block until it finishes or times out.
   *
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

  /**
   * Load the MRZ templates bundled with the SDK. Called once when the first
   * scanning screen is opened; it is a no-op when the templates are already loaded.
   */
  @Synchronized
  fun ensureTemplates() {
    if (templateReady) {
      return
    }
    try {
      router.initSettingsFromFile("mrzscanner-mobile-templates.json")
      templateReady = true
    } catch (e: CaptureVisionRouterException) {
      throw ScannerException("Failed to load MRZ templates: ${e.message}")
    }
  }

  /** Parse a document from a still image. This is the "file" data source. */
  fun parseBitmap(bitmap: Bitmap?): CapturedResult {
    if (bitmap == null) {
      throw ScannerException("Source bitmap is null")
    }
    return ensureParsed(router.capture(bitmap, template()))
  }

  /** Parse a document from a local file path. This is the "file" data source. */
  fun parseFile(path: String): CapturedResult {
    if (path.isEmpty()) {
      throw ScannerException("Source path is empty")
    }
    return ensureParsed(router.capture(path, template()))
  }

  private fun ensureParsed(result: CapturedResult?): CapturedResult {
    if (result == null) {
      throw ScannerException("No result was produced for the input image")
    }
    val parsed: ParsedResult? = result.parsedResult
    if (parsed == null || parsed.items == null || parsed.items.isEmpty()) {
      throw ScannerException(
        "No MRZ was found in the image. Make sure the Machine-Readable Zone is clearly visible."
      )
    }
    return result
  }

  /** Error type raised by the scanner engine. */
  class ScannerException(message: String) : Exception(message)
}
