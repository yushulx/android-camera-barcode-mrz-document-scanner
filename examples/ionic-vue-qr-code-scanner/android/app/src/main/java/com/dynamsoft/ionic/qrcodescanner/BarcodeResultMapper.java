package com.dynamsoft.ionic.qrcodescanner;

import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.dbr.BarcodeResultItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Converts Dynamsoft {@link BarcodeResultItem} instances into plain JSON so that the
 * Capacitor bridge can hand them to the web layer with no SDK types involved.
 */
final class BarcodeResultMapper {

    private BarcodeResultMapper() {
    }

    static JSONObject toJson(BarcodeResultItem item) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("text", item.getText());
        json.put("formatString", item.getFormatString());
        json.put("points", pointsToJson(item.getLocation()));
        return json;
    }

    static JSONArray toJson(BarcodeResultItem[] items) throws JSONException {
        JSONArray array = new JSONArray();
        if (items == null) {
            return array;
        }
        for (BarcodeResultItem item : items) {
            array.put(toJson(item));
        }
        return array;
    }

    private static JSONArray pointsToJson(Quadrilateral location) throws JSONException {
        JSONArray array = new JSONArray();
        if (location == null || location.points == null) {
            return array;
        }
        for (android.graphics.Point point : location.points) {
            JSONObject p = new JSONObject();
            p.put("x", point.x);
            p.put("y", point.y);
            array.put(p);
        }
        return array;
    }
}
