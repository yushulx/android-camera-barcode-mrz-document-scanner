package com.dynamsoft.dbrbundle.ui;

import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.dbr.BarcodeResultItem;

/**
 * @author: dynamsoft
 * Time: 2024/11/28
 * Description:
 */
public class BarcodeResultItemProxy extends BarcodeResultItem {
	private final long format;
	private final String formatString;
	private final String text;
	private final byte[] bytes;
	private final Quadrilateral location;
	private final int confidence;
	private final int angle;
	private final int moduleSize;
	private final boolean mirrored;
	private final boolean dpm;
	private final String taskName;
	private final String targetROIDefName;

	protected BarcodeResultItemProxy(long instance, long format, String formatString,
	                                 String text, byte[] bytes, Quadrilateral location,
	                                 int confidence, int angle, int moduleSize,
	                                 boolean mirrored, boolean dpm, String taskName, String targetROIDefName) {
		super(instance);
		this.format = format;
		this.formatString = formatString;
		this.text = text;
		this.bytes = bytes;
		this.location = location;
		this.confidence = confidence;
		this.angle = angle;
		this.moduleSize = moduleSize;
		this.mirrored = mirrored;
		this.dpm = dpm;
		this.taskName = taskName;
		this.targetROIDefName = targetROIDefName;
	}

	public long getFormat() {
		return this.format;
	}

	public String getFormatString() {
		return this.formatString;
	}

	public String getText() {
		return this.text;
	}

	public byte[] getBytes() {
		return this.bytes;
	}

	public Quadrilateral getLocation() {
		return this.location;
	}

	public int getConfidence() {
		return this.confidence;
	}

	public int getAngle() {
		return this.angle;
	}

	public int getModuleSize() {
		return this.moduleSize;
	}

	public boolean isMirrored() {
		return this.mirrored;
	}

	public boolean isDPM() {
		return this.dpm;
	}

	public String getTaskName() {
		return this.taskName;
	}

	public String getTargetROIDefName() {
		return this.targetROIDefName;
	}
}
