package com.dynamsoft.dbrbundle.ui;

import com.dynamsoft.dbr.EnumBarcodeFormat;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author: dynamsoft
 * Time: 2024/11/28
 * Description:
 */
class ConfigSerial implements Serializable {
	private final boolean isFlashButtonVisible;
	private final boolean isBeepEnabled;
	private final boolean isScanLaserVisible;
	private final boolean isAutoZoomEnabled;
	private final boolean isCloseButtonVisible;
	private final long format;
	private final String templateFilePath;
	private final String templateFile;
	private final String license;
	private final ArrayList<Float> dsRect;
	private final int scanningMode;
	private final int maxConsecutiveStableFramesToExit;
	private final int expectedBarcodesCount;
	private final boolean isCameraToggleButtonVisible;

	public ConfigSerial(boolean isFlashButtonVisible, boolean isBeepEnabled,
	                    boolean isScanLaserVisible, boolean isAutoZoomEnabled, boolean isCloseButtonVisible,
	                    long format, String templateFilePath, String license, ArrayList<Float> dsRect,
	                    @EnumScanningMode int scanningMode, int maxConsecutiveStableFramesToExit, int expectedBarcodesCount,
	                    boolean isCameraToggleButtonVisible, String templateFile) {
		this.isFlashButtonVisible = isFlashButtonVisible;
		this.isBeepEnabled = isBeepEnabled;
		this.isScanLaserVisible = isScanLaserVisible;
		this.isAutoZoomEnabled = isAutoZoomEnabled;
		this.isCloseButtonVisible = isCloseButtonVisible;
		this.format = format;
		this.templateFilePath = templateFilePath;
		this.license = license;
		this.dsRect = dsRect;
		this.scanningMode = scanningMode;
		this.maxConsecutiveStableFramesToExit = maxConsecutiveStableFramesToExit;
		this.expectedBarcodesCount = expectedBarcodesCount;
		this.isCameraToggleButtonVisible = isCameraToggleButtonVisible;
		this.templateFile = templateFile;
	}

	public boolean isTorchButtonVisible() {
		return isFlashButtonVisible;
	}

	public boolean isBeepEnabled() {
		return isBeepEnabled;
	}

	public boolean isScanLaserVisible() {
		return isScanLaserVisible;
	}

	public boolean isAutoZoomEnabled() {
		return isAutoZoomEnabled;
	}

	public boolean isCloseButtonVisible() {
		return isCloseButtonVisible;
	}

	@EnumBarcodeFormat
	public long getBarcodeFormats() {
		return format;
	}

	public String getTemplateFilePath() {
		return templateFilePath;
	}

	public String getLicense() {
		return license;
	}

	public ArrayList<Float> getScanRegion() {
		return dsRect;
	}

	@EnumScanningMode
	public int getScanningMode() {
		return scanningMode;
	}

	public int getMaxConsecutiveStableFramesToExit() {
		return maxConsecutiveStableFramesToExit;
	}

	public int getExpectedBarcodesCount() {
		return expectedBarcodesCount;
	}

	public boolean isCameraToggleButtonVisible() {
		return isCameraToggleButtonVisible;
	}

	public String getTemplateFile() {
		return templateFile;
	}
}
