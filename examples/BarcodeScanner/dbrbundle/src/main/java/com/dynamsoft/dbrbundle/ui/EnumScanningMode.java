package com.dynamsoft.dbrbundle.ui;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

import static com.dynamsoft.dbrbundle.ui.EnumScanningMode.SM_MULTIPLE;
import static com.dynamsoft.dbrbundle.ui.EnumScanningMode.SM_SINGLE;

/**
 * @author: dynamsoft
 * Time: 2024/12/31
 * Description:
 */
@IntDef({SM_SINGLE, SM_MULTIPLE})
@Retention(RetentionPolicy.SOURCE)
public @interface EnumScanningMode {
	int SM_SINGLE = 0;
	int SM_MULTIPLE = 1;
}

