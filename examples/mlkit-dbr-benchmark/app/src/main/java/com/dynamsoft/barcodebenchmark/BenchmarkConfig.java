package com.dynamsoft.barcodebenchmark;

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
