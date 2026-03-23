import React, {useEffect, useRef, useState} from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  CameraEnhancer,
  CameraView,
  CaptureVisionRouter,
  EnumCapturedResultItemType,
  EnumCrossVerificationStatus,
  EnumPresetTemplate,
  MultiFrameResultCrossFilter,
} from 'dynamsoft-capture-vision-react-native';
import {StackNavigation} from './App.tsx';
import {ImageData} from 'dynamsoft-capture-vision-react-native';

// Module-level flag: one-time SDK wiring (setInput + addFilter) should only happen once
// since CameraEnhancer and CaptureVisionRouter are singletons.
let sdkInitialized = false;

export function Scanner({navigation}: StackNavigation): React.JSX.Element {
  const ifBtnClick = useRef(false);
  const cameraView = useRef<CameraView>(null);
  // Use refs so singleton instances are captured once and stable across renders.
  const cameraRef = useRef<CameraEnhancer>(CameraEnhancer.getInstance());
  const cvrRef = useRef<CaptureVisionRouter>(CaptureVisionRouter.getInstance());
  const receiverRef = useRef<any>(null);
  const [error, setError] = useState<string | null>(null);
  const insets = useSafeAreaInsets();

  useFocusEffect(
    React.useCallback(() => {
      const camera = cameraRef.current;
      //Open camera when the Scanner screen is focused.
      camera.open();

      return () => {
        //Close camera when the Scanner screen is unfocused.
        camera.close();
      };
    }, []),
  );

  useEffect(() => {
    const camera = cameraRef.current;
    const cvr = cvrRef.current;
    let cancelled = false;

    // Connect view synchronously so camera.open() in useFocusEffect sees it.
    if (cameraView.current) {
      camera.setCameraView(cameraView.current);
    }

    const setup = async () => {
      try {
        await CameraEnhancer.requestCameraPermission();
      } catch (e: any) {
        console.error('Camera permission request failed: ' + e.message);
        setError('Camera permission denied.');
        return;
      }

      // Ensure any previous capturing session is fully stopped before re-initializing.
      await cvr.stopCapturing().catch(() => {});

      if (cancelled) {
        return;
      }

      // One-time singelton wiring — setInput and addFilter are cumulative on
      // the singleton so they must only be called once across the app lifetime.
      if (!sdkInitialized) {
        cvr.setInput(camera);
        const filter = new MultiFrameResultCrossFilter();
        filter.enableResultCrossVerification(EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE, true);
        cvr.addFilter(filter);
        sdkInitialized = true;
      }

      receiverRef.current = cvr.addResultReceiver({
        onProcessedDocumentResultReceived: result => {
          if (
            result.deskewedImageResultItems &&
            result.deskewedImageResultItems.length > 0 &&
            (ifBtnClick.current || result.deskewedImageResultItems[0].crossVerificationStatus === EnumCrossVerificationStatus.CVS_PASSED)
          ) {
            ifBtnClick.current = false;
            global.originalImage = cvr.getIntermediateResultManager().getOriginalImage(result.originalImageHashId) as ImageData;
            global.deskewedImage = result.deskewedImageResultItems[0].imageData;
            global.sourceDeskewQuad = result.deskewedImageResultItems[0].sourceDeskewQuad;
            if (global.originalImage.width > 0 && global.originalImage.height > 0) {
              navigation.navigate('NormalizedImage');
            }
          }
        },
      });

      cvr.startCapturing(EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT).catch(e => {
        console.error('Failed to start capturing: ' + e.message);
        if (!cancelled) {
          setError('Failed to start document scanning.');
        }
      });
    };

    setup();

    return () => {
      cancelled = true;
      if (receiverRef.current) {
        cvr.removeResultReceiver(receiverRef.current);
        receiverRef.current = null;
      }
      cvr.stopCapturing().catch(e => console.error('stopCapturing error: ' + e.message));
      camera.setCameraView(null);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigation]);

  return (
    <CameraView style={styles.fullScreen} ref={cameraView}>
      {/* Top bar */}
      <View style={[styles.topBar, {paddingTop: insets.top + 10}]}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton} hitSlop={8}>
          <Text style={styles.backText}>‹  Back</Text>
        </TouchableOpacity>
        <Text style={styles.topTitle}>Scan Document</Text>
        <View style={styles.backButton} />
      </View>

      {/* Hint pill */}
      {!error && (
        <View style={styles.hintContainer}>
          <Text style={styles.hintText}>Point camera at a document — it will auto-capture</Text>
        </View>
      )}

      {/* Error toast */}
      {error ? (
        <View style={styles.errorToast}>
          <Text style={styles.errorToastText}>⚠️  {error}</Text>
        </View>
      ) : null}

      {/* Bottom capture bar */}
      <View style={[styles.bottomBar, {paddingBottom: insets.bottom + 20}]}>
        <Text style={styles.captureHint}>or tap to capture manually</Text>
        <TouchableOpacity
          style={styles.shutterOuter}
          activeOpacity={0.8}
          onPress={() => (ifBtnClick.current = true)}>
          <View style={styles.shutterInner} />
        </TouchableOpacity>
      </View>
    </CameraView>
  );
}

const styles = StyleSheet.create({
  fullScreen: {flex: 1},
  // ─── Top bar ────────────────────────────────────────────────────────────────
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingBottom: 14,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  backButton: {minWidth: 60},
  backText: {color: '#FFFFFF', fontSize: 17},
  topTitle: {color: '#FFFFFF', fontSize: 17, fontWeight: '700'},
  // ─── Hint ───────────────────────────────────────────────────────────────────
  hintContainer: {
    alignSelf: 'center',
    marginTop: 14,
    backgroundColor: 'rgba(0,0,0,0.45)',
    borderRadius: 20,
    paddingHorizontal: 18,
    paddingVertical: 7,
  },
  hintText: {color: '#E2E8F0', fontSize: 13},
  // ─── Error ──────────────────────────────────────────────────────────────────
  errorToast: {
    position: 'absolute',
    top: 110,
    left: 20,
    right: 20,
    backgroundColor: '#FEF2F2',
    borderRadius: 10,
    padding: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#EF4444',
  },
  errorToastText: {color: '#991B1B', fontSize: 14, fontWeight: '600'},
  // ─── Bottom capture bar ──────────────────────────────────────────────────────
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingTop: 16,
  },
  captureHint: {color: '#94A3B8', fontSize: 12, marginBottom: 16},
  shutterOuter: {
    width: 74,
    height: 74,
    borderRadius: 37,
    borderWidth: 4,
    borderColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutterInner: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: '#FFFFFF',
  },
});
