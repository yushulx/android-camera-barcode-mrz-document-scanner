package expo.modules.dynamsoftbarcodescanner

import com.dynamsoft.dbr.BarcodeResultItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts Dynamsoft [BarcodeResultItem] instances to/from plain JSON so the
 * scanner Activity and the Expo module can exchange them with no SDK types
 * crossing the bridge.
 */
object BarcodeResults {

  fun toJsonArray(items: Array<BarcodeResultItem>?): JSONArray {
    val array = JSONArray()
    items?.forEach { array.put(toJson(it)) }
    return array
  }

  private fun toJson(item: BarcodeResultItem): JSONObject {
    val json = JSONObject()
    json.put("text", item.text)
    json.put("formatString", item.formatString)
    json.put("points", pointsToJson(item.location))
    return json
  }

  private fun pointsToJson(location: com.dynamsoft.core.basic_structures.Quadrilateral?): JSONArray {
    val array = JSONArray()
    location?.points?.forEach { point ->
      val p = JSONObject()
      p.put("x", point.x)
      p.put("y", point.y)
      array.put(p)
    }
    return array
  }

  /** Parse a serialized results JSON array into the bridge-ready map list. */
  fun fromJson(json: String): List<Map<String, Any?>> {
    val array = JSONArray(json)
    val out = ArrayList<Map<String, Any?>>(array.length())
    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      val points = ArrayList<Map<String, Any?>>()
      val pts = obj.optJSONArray("points")
      if (pts != null) {
        for (j in 0 until pts.length()) {
          val p = pts.getJSONObject(j)
          points.add(mapOf("x" to p.optDouble("x"), "y" to p.optDouble("y")))
        }
      }
      out.add(
        mapOf(
          "text" to obj.optString("text"),
          "formatString" to obj.optString("formatString"),
          "points" to points
        )
      )
    }
    return out
  }
}