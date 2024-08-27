#include <jni.h>
#include <cstring>
#include "DynamsoftBarcodeReader.h"
#include <android/log.h>

#define LOG_TAG "BarcodeReader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
/**
 * BarcodeReader object:
 *
 */
void *pBarcodeReader = nullptr;

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_android_camera2basic_Camera2BasicFragment_readBarcode(JNIEnv *env, jobject instance, jlong hBarcode,
                                                                       jobject byteBuffer, jint width, jint height, jint stride) {

    // ArrayList
    jclass classArray = env->FindClass("java/util/ArrayList");
    if (classArray == NULL) return NULL;
    jmethodID midArrayInit =  env->GetMethodID(classArray, "<init>", "()V");
    if (midArrayInit == NULL) return NULL;
    jobject objArr = env->NewObject(classArray, midArrayInit);
    if (objArr == NULL) return NULL;
    jmethodID midAdd = env->GetMethodID(classArray, "add", "(Ljava/lang/Object;)Z");
    if (midAdd == NULL) return NULL;

    // SimpleResult
    jclass cls = env->FindClass("com/example/android/camera2basic/Camera2BasicFragment$SimpleResult");
    if (NULL == cls) return NULL;
    jmethodID midInit = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (NULL == midInit) return NULL;


    unsigned char * buffer = (unsigned char*)env->GetDirectBufferAddress(byteBuffer);

    int ret = DBR_DecodeBuffer((void *)hBarcode, buffer, width, height, stride, IPF_NV21, "");

    if (ret) {
        LOGE("Detection error: %s", DBR_GetErrorString(ret));
//        return NULL;
    }

    TextResultArray *pResults = NULL;
    DBR_GetAllTextResults((void *)hBarcode, &pResults);
    if (pResults)
    {
        int count = pResults->resultsCount;

        for (int i = 0; i < count; i++)
        {
//            LOGI("Native format: %s, text: %s", pResults->ppResults[i]->pszBarcodeFormatString, pResults->ppResults[i]->pszBarcodeText);
            jobject newObj = env->NewObject(cls, midInit, env->NewStringUTF(pResults->results[i]->barcodeFormatString), env->NewStringUTF(pResults->results[i]->barcodeText));
            env->CallBooleanMethod(objArr, midAdd, newObj);
        }

        // release memory of barcode results
        DBR_FreeTextResults(&pResults);
    }
    return objArr;

}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_android_camera2basic_Camera2BasicFragment_createBarcodeReader(JNIEnv *env, jobject instance, jstring license) {
    if (!pBarcodeReader) {
        // Instantiate barcode reader object.
        pBarcodeReader = DBR_CreateInstance();

        // Initialize the license key.
        const char *nativeString = env->GetStringUTFChars(license, 0);
        char errorMsgBuffer[512];
        DBR_InitLicense(nativeString, errorMsgBuffer, 512);
        env->ReleaseStringUTFChars(license, nativeString);
    }

    return (jlong)(pBarcodeReader);

}

extern "C" JNIEXPORT void JNICALL
Java_com_example_android_camera2basic_Camera2BasicFragment_destroyBarcodeReader(JNIEnv *env, jobject instance, jlong hBarcode) {

    if (hBarcode) {
        DBR_DestroyInstance((void *)hBarcode);
    }

}
