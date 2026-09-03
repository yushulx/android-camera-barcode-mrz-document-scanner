package expo.modules.dynamsoftmrzscanner

import android.graphics.Bitmap
import android.util.Base64
import com.dynamsoft.cvr.CapturedResult
import com.dynamsoft.dcp.ParsedResultItem
import com.dynamsoft.ddn.DeskewedImageResultItem
import com.dynamsoft.ddn.ProcessedDocumentResult
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Converts scan payloads to/from plain JSON so the scanner Activity and the
 * Expo module can exchange them with no SDK types crossing the bridge, and
 * rebuilds the bridge-ready payload from a single-file capture result.
 */
object IdScanResultPayload {

  /** Parse a serialized scanner-activity JSON payload into the bridge-ready map. */
  fun fromActivityJson(json: String): Map<String, Any?> {
    val root = JSONObject(json)
    val fields = HashMap<String, Any?>()
    val fieldsJson = root.optJSONObject("fields") ?: JSONObject()
    val keys = fieldsJson.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      fields[key] = fieldsJson.optString(key)
    }
    val payload = HashMap<String, Any?>()
    payload["fields"] = fields
    root.optString("portraitBase64").takeIf { it.isNotEmpty() }
      ?.let { payload["portraitBase64"] = it }
    root.optString("documentImageBase64").takeIf { it.isNotEmpty() }
      ?.let { payload["documentImageBase64"] = it }
    return payload
  }

  /**
   * Build the bridge payload from a single-file capture result:
   * parsed MRZ fields plus the deskewed document image when available.
   */
  fun fromFileResult(captured: CapturedResult): Map<String, Any?> {
    val item: ParsedResultItem = captured.parsedResult!!.items[0]
    val fieldsJson = JSONObject(IdResultFormatter.toFieldMap(item))
    val fields = HashMap<String, Any?>()
    val keys = fieldsJson.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      fields[key] = fieldsJson.optString(key)
    }
    val payload = HashMap<String, Any?>()
    payload["fields"] = fields
    val doc: ProcessedDocumentResult? = captured.processedDocumentResult
    if (doc != null && doc.deskewedImageResultItems != null && doc.deskewedImageResultItems.isNotEmpty()) {
      val deskewed: DeskewedImageResultItem = doc.deskewedImageResultItems[0]
      val imageData = deskewed.imageData
      if (imageData != null) {
        val bitmap: Bitmap? = imageData.toBitmap()
        if (bitmap != null) {
          payload["documentImageBase64"] = toBase64(bitmap)
        }
      }
    }
    return payload
  }

  private fun toBase64(bitmap: Bitmap): String {
    val os = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)
    return "data:image/jpeg;base64," + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
  }
}
