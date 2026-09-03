package expo.modules.dynamsoftmrzscanner

import android.content.Intent

/** Contract shared between the scanner Activity and the Expo module. */
object IdScanResultContract {

  /** JSON payload produced by a successful scan. */
  const val EXTRA_JSON = "expo.modules.dynamsoftmrzscanner.JSON"

  /** Error message produced by a failed scan. */
  const val EXTRA_ERROR = "expo.modules.dynamsoftmrzscanner.ERROR"

  fun ok(json: String): Intent =
    Intent().putExtra(EXTRA_JSON, json)

  fun error(message: String): Intent =
    Intent().putExtra(EXTRA_ERROR, message)
}
