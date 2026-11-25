package com.dynamsoft.barcodebenchmark;

import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.SimplifiedCaptureVisionSettings;
import com.dynamsoft.dbr.EnumBarcodeFormat;
import java.util.EnumSet;
import zxingcpp.BarcodeReader;

/**
 * Global configuration class for benchmark settings and Dynamsoft templates
 */
public class BenchmarkConfig {
    
    /**
     * Toggle to show/hide benchmark time in UI
     */
    public static boolean SHOW_BENCHMARK_TIME = false;
    
    /**
     * Toggle to use custom Dynamsoft template or default built-in template
     * When true: Uses DYNAMSOFT_TEMPLATE_JSON
     * When false: Uses default built-in template (EnumPresetTemplate.PT_READ_BARCODES)
     */
    public static boolean USE_CUSTOM_TEMPLATE = false;

    /**
     * Master switch for the ZXing-C++ third reader.
     *
     * The zxing-cpp Android wrapper (3.0.x and 3.1.x) aborts the whole process on
     * images where a decoded symbol maps to a format id its JNI layer does not
     * know: "JNI DETECTED ERROR ... Invalid BarcodeFormat" followed by SIGABRT.
     * A pending-exception abort inside JNI cannot be caught in Java, so the only
     * reliable containment is not to call the reader. With the flag off the app
     * runs Dynamsoft + ML Kit only, and every ZXing entry point is skipped.
     * Flip it to true for a build that wants the third reader and accepts the
     * crash risk.
     */
    public static boolean ENABLE_ZXING = false;

    /**
     * Restrict the ZXing-C++ reader to the symbologies this benchmark scores.
     *
     * The wrapper aborts the process when a decoded symbol carries a format id
     * its Kotlin layer does not know. Naming the formats up front keeps the
     * decoder away from the long tail (DataBar, Telepen, Code 32, ...), but a
     * 1,300-image pass still aborted around image 1,076 on 3.1.1, so the switch
     * above stays off until the upstream wrapper clears the exception before it
     * returns into JNI.
     */
    public static void configureZxing(BarcodeReader reader) {
        reader.getOptions().setTryHarder(true);
        reader.getOptions().setTryRotate(true);
        reader.getOptions().setFormats(EnumSet.of(
                BarcodeReader.Format.AZTEC,
                BarcodeReader.Format.CODABAR,
                BarcodeReader.Format.CODE_39,
                BarcodeReader.Format.CODE_93,
                BarcodeReader.Format.CODE_128,
                BarcodeReader.Format.DATA_MATRIX,
                BarcodeReader.Format.EAN_8,
                BarcodeReader.Format.EAN_13,
                BarcodeReader.Format.ITF,
                BarcodeReader.Format.MAXI_CODE,
                BarcodeReader.Format.PDF_417,
                BarcodeReader.Format.QR_CODE,
                BarcodeReader.Format.MICRO_QR_CODE,
                BarcodeReader.Format.UPC_A,
                BarcodeReader.Format.UPC_E));
    }

    /**
     * Apply the benchmark configuration to a router.
     *
     * The benchmark keeps the built-in ReadBarcodes_Default preset and only widens
     * its barcode format list to BF_ALL. ReadBarcodes_Default ships BF_DEFAULT,
     * which omits postal symbologies, MaxiCode, DotCode, MSI, Pharmacode and
     * Micro QR - formats that appear in the synthetic benchmark dataset. This
     * mirrors the desktop runner used for that dataset (ReadBarcodes_Default
     * with BF_ALL), so the Android and desktop numbers are comparable.
     */
    public static void applyDynamsoftSettings(CaptureVisionRouter router)
            throws CaptureVisionRouterException {
        if (USE_CUSTOM_TEMPLATE) {
            router.initSettings(DYNAMSOFT_TEMPLATE_JSON);
            return;
        }
        SimplifiedCaptureVisionSettings settings =
                router.getSimplifiedSettings(EnumPresetTemplate.PT_READ_BARCODES);
        settings.barcodeSettings.barcodeFormatIds = EnumBarcodeFormat.BF_ALL;
        router.updateSettings(EnumPresetTemplate.PT_READ_BARCODES, settings);
    }
    
    /**
     * Dynamsoft Capture Vision Router template JSON
     * This template is optimized for barcode scanning with specific performance settings
     */
    public static final String DYNAMSOFT_TEMPLATE_JSON = "{\n" +
            "    \"GlobalParameter\":\n" +
            "    {\n" +
            "        \"IntraOpNumThreads\": 2\n" +
            "    },\n" +
            "    \"CaptureVisionTemplates\": [\n" +
            "        {\n" +
            "            \"ImageROIProcessingNameArray\": [\n" +
            "                \"ROI_Default\"\n" +
            "            ],\n" +
            "            \"Name\": \"ReadBarcodes_Default\",\n" +
            "            \"MaxParallelTasks\": 0,\n" +
            "            \"Timeout\": 100\n" +
            "        }\n" +
            "    ],\n" +
            "    \"TargetROIDefOptions\": [\n" +
            "        {\n" +
            "            \"Name\": \"ROI_Default\",\n" +
            "            \"TaskSettingNameArray\": [\n" +
            "                \"Task_Default\"\n" +
            "            ]\n" +
            "        }\n" +
            "    ],\n" +
            "    \"BarcodeReaderTaskSettingOptions\": [\n" +
            "        {\n" +
            "            \"Name\": \"Task_Default\",\n" +
            "            \"BarcodeFormatIds\": [\n" +
            "                \"BF_DEFAULT\"\n" +
            "            ],\n" +
            "            \"ExpectedBarcodesCount\": 0,\n" +
            "            \"MaxThreadsInOneTask\": 1,\n" +
            "            \"SectionArray\": [\n" +
            "                {\n" +
            "                    \"ImageParameterName\": \"ip\",\n" +
            "                    \"Section\": \"ST_BARCODE_LOCALIZATION\",\n" +
            "                    \"StageArray\": [\n" +
            "                        {\n" +
            "                            \"Stage\": \"SST_LOCALIZE_CANDIDATE_BARCODES\",\n" +
            "                            \"LocalizationModes\": [\n" +
            "                                {\n" +
            "                                    \"Mode\": \"LM_SCAN_DIRECTLY\",\n" +
            "                                    \"ModelNameArray\": null\n" +
            "                                }\n" +
            "                            ]\n" +
            "                        }\n" +
            "                    ]\n" +
            "                },\n" +
            "                {\n" +
            "                    \"ImageParameterName\": \"ip\",\n" +
            "                    \"Section\": \"ST_BARCODE_DECODING\",\n" +
            "                    \"StageArray\": [\n" +
            "                        {\n" +
            "                            \"Stage\": \"SST_DECODE_BARCODES\",\n" +
            "                            \"DeblurModes\": [\n" +
            "                                {\n" +
            "                                    \"Mode\": \"DM_DIRECT_BINARIZATION\"\n" +
            "                                }\n" +
            "                            ]\n" +
            "                        }\n" +
            "                    ]\n" +
            "                }\n" +
            "            ]\n" +
            "        }\n" +
            "    ],\n" +
            "    \"ImageParameterOptions\": [\n" +
            "        {\n" +
            "            \"ApplicableStages\": [\n" +
            "                {\n" +
            "                    \"ImageScaleSetting\": {\n" +
            "                        \"EdgeLengthThreshold\": 100000,\n" +
            "                        \"ScaleType\": \"ST_SCALE_DOWN\"\n" +
            "                    },\n" +
            "                    \"Stage\": \"SST_SCALE_IMAGE\"\n" +
            "                },\n" +
            "                {\n" +
            "                    \"GrayscaleTransformationModes\": [\n" +
            "                        {\n" +
            "                            \"Mode\": \"GTM_ORIGINAL\"\n" +
            "                        }\n" +
            "                    ],\n" +
            "                    \"Stage\": \"SST_TRANSFORM_GRAYSCALE\"\n" +
            "                }\n" +
            "            ],\n" +
            "            \"Name\": \"ip\"\n" +
            "        }\n" +
            "    ]\n" +
            "}";
}
