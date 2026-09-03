package expo.modules.dynamsoftbarcodescanner

import android.content.Intent

/** Contract shared between the scanner Activity and the Expo module. */
object ScanResultContract {

  /** JSON payload produced by a successful scan. */
  const val EXTRA_RESULTS = "expo.modules.dynamsoftbarcodescanner.RESULTS"

  /** Error message produced by a failed scan. */
  const val EXTRA_ERROR = "expo.modules.dynamsoftbarcodescanner.ERROR"

  fun ok(resultsJson: String): Intent =
    Intent().putExtra(EXTRA_RESULTS, resultsJson)

  fun error(message: String): Intent =
    Intent().putExtra(EXTRA_ERROR, message)
}