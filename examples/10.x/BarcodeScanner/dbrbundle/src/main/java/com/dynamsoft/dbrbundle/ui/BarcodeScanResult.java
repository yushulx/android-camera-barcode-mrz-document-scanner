package com.dynamsoft.dbrbundle.ui;

import android.content.Intent;
import android.graphics.Point;

import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.dbr.BarcodeResultItem;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.IntDef;

import static com.dynamsoft.dbrbundle.ui.BarcodeScannerActivity.EXTRA_ERROR_CODE;
import static com.dynamsoft.dbrbundle.ui.BarcodeScannerActivity.EXTRA_ERROR_STRING;
import static com.dynamsoft.dbrbundle.ui.BarcodeScannerActivity.EXTRA_STATUS_CODE;

/**
 * @author: dynamsoft
 * Time: 2024/10/8
 * Description:
 */
public final class BarcodeScanResult {
	public static final String TAG = "ScannerResult";
	private BarcodeResultItemProxy[] barcodeResults;
	@EnumResultStatus
	private int resultStatus;
	private int errorCode;
	private String errorString;

	@IntDef(value = {EnumResultStatus.RS_FINISHED, EnumResultStatus.RS_CANCELED, EnumResultStatus.RS_EXCEPTION})
	public @interface EnumResultStatus {
		int RS_FINISHED = 0;
		int RS_CANCELED = 1;
		int RS_EXCEPTION = 2;
	}

	public BarcodeScanResult(int resultCode, Intent data) {
		if (data != null) {
			HashMap<Integer, HashMap<String, Object>> barcodeResultsMap = (HashMap<Integer, HashMap<String, Object>>) data.getSerializableExtra(BarcodeScannerActivity.EXTRA_ITEM_LIST);
			resolveIntentData(barcodeResultsMap);
			this.resultStatus = data.getIntExtra(EXTRA_STATUS_CODE, 0);
			this.errorCode = data.getIntExtra(EXTRA_ERROR_CODE, 0);
			this.errorString = data.getStringExtra(EXTRA_ERROR_STRING);
		}
	}

	public BarcodeResultItem[] getBarcodes() {
		return barcodeResults;
	}


	@EnumResultStatus
	public int getResultStatus() {
		return resultStatus;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String getErrorString() {
		return errorString;
	}

	private void resolveIntentData(HashMap<Integer, HashMap<String, Object>> barcodeResultsMap){
		if (barcodeResultsMap != null) {
			barcodeResults = new BarcodeResultItemProxy[barcodeResultsMap.size()];
			for (HashMap.Entry<Integer, HashMap<String, Object>> entry : barcodeResultsMap.entrySet()) {
				HashMap<String, Object> itemMap = entry.getValue();
				String text = (String) itemMap.get("text");
				long type = (long) itemMap.get("type");
				String typeString = (String) itemMap.get("typeString");
				byte[] bytes = (byte[]) itemMap.get("bytes");

				List<Integer> pointsList = (List<Integer>) itemMap.get("location");
				Point[] points = new Point[pointsList.size() / 2];
				for (int i = 0; i < points.length; i++) {
					int x = pointsList.get(2 * i);
					int y = pointsList.get(2 * i + 1);
					points[i] = new Point(x, y);
				}

				int confidence = (int) itemMap.get("confidence");
				int angle = (int) itemMap.get("angle");
				boolean mirrored = (boolean) itemMap.get("mirrored");
				int moduleSize = (int) itemMap.get("moduleSize");
				boolean dpm = (boolean) itemMap.get("dpm");
				String taskName = (String) itemMap.get("taskName");
				String targetROI = (String) itemMap.get("targetROIDefName");
				Quadrilateral location = new Quadrilateral();
				location.points = points;
				BarcodeResultItemProxy itemProxy = new BarcodeResultItemProxy(0L, type,
						typeString, text, bytes, location, confidence, angle, moduleSize, mirrored, dpm, taskName, targetROI);
				barcodeResults[entry.getKey()] = itemProxy;
			}
		}
	}
}
