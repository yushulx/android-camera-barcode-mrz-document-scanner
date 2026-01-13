package com.dynamsoft.dbrbundle.ui;

import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.dbr.EnumBarcodeFormat;

/**
 * @author: dynamsoft
 * Time: 2024/10/8
 * Description:
 */
public final class BarcodeScannerConfig {
	public static final String TAG = "BarcodeScannerConfiguration";
	private boolean isFlashButtonVisible = true;
	private boolean isBeepEnabled;
	private boolean isScanLaserVisible = true;
	private boolean isAutoZoomEnabled;
	private boolean isCloseButtonVisible = true;
	private boolean isCameraToggleButtonVisible;
	private long format = EnumBarcodeFormat.BF_DEFAULT;
	@Deprecated
	private String templateFilePath;
	private String templateFile;
	private String license;
	private DSRect dsRect;
	private int scanningMode = 0;
	private int maxConsecutiveStableFramesToExit = 10;
	private int expectedBarcodesCount = 999;

	public boolean isTorchButtonVisible() {
		return isFlashButtonVisible;
	}

	public void setTorchButtonVisible(boolean flashButtonVisible) {
		isFlashButtonVisible = flashButtonVisible;
	}

	public boolean isBeepEnabled() {
		return isBeepEnabled;
	}

	public void setBeepEnabled(boolean beepEnabled) {
		isBeepEnabled = beepEnabled;
	}

	public boolean isScanLaserVisible() {
		return isScanLaserVisible;
	}

	public void setScanLaserVisible(boolean scanLaserVisible) {
		isScanLaserVisible = scanLaserVisible;
	}

	public boolean isAutoZoomEnabled() {
		return isAutoZoomEnabled;
	}

	public void setAutoZoomEnabled(boolean autoZoomEnabled) {
		isAutoZoomEnabled = autoZoomEnabled;
	}

	public boolean isCloseButtonVisible() {
		return isCloseButtonVisible;
	}

	public void setCloseButtonVisible(boolean closeButtonVisible) {
		isCloseButtonVisible = closeButtonVisible;
	}

	@EnumBarcodeFormat
	public long getBarcodeFormats() {
		return format;
	}

	public void setBarcodeFormats(long format) {
		this.format = format;
	}

	@Deprecated
	public String getTemplateFilePath() {
		return templateFilePath;
	}

	@Deprecated
	public void setTemplateFilePath(String templateFilePath) {
		this.templateFilePath = templateFilePath;
	}

	public String getTemplateFile(){
		return templateFile;
	}

	public void setTemplateFile(String templateFilePath){
		this.templateFile = templateFilePath;
	}

	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public DSRect getScanRegion(){
		return dsRect;
	}
	public void setScanRegion(DSRect scanRegion){
		this.dsRect = scanRegion;
	}

	@EnumScanningMode
	public int getScanningMode(){
		return scanningMode;
	}

	public void setScanningMode(@EnumScanningMode int scanningMode){
		this.scanningMode = scanningMode;
	}

	public int getMaxConsecutiveStableFramesToExit(){
		return maxConsecutiveStableFramesToExit;
	}

	public void setMaxConsecutiveStableFramesToExit(int maxConsecutiveStableFramesToExit){
		this.maxConsecutiveStableFramesToExit = maxConsecutiveStableFramesToExit;
	}

	public int getExpectedBarcodesCount() {
		return expectedBarcodesCount;
	}

	public void setExpectedBarcodesCount(int expectedBarcodesCount) {
		this.expectedBarcodesCount = expectedBarcodesCount;
	}

	public boolean isCameraToggleButtonVisible() {
		return isCameraToggleButtonVisible;
	}

	public void setCameraToggleButtonVisible(boolean cameraToggleButtonVisible) {
		isCameraToggleButtonVisible = cameraToggleButtonVisible;
	}
}
