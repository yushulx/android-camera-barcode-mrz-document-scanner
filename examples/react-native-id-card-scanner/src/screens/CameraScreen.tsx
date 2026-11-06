import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  CameraEnhancer,
  CameraView,
  CaptureVisionRouter,
  EnumColourChannelUsageType,
  EnumCapturedResultItemType,
  EnumCrossVerificationStatus,
  EnumPresetTemplate,
  EnumResolution,
  MultiFrameResultCrossFilter,
  imageDataToBase64,
  type CapturedResult,
  type CapturedResultReceiver,
  type ImageData,
} from 'dynamsoft-capture-vision-react-native';
import {cardInfoFromMRZ} from '../services/mrz';
import {CardDraft} from '../services/CardDraft';
import type {CardInfo} from '../models/ScannedIDCard';
import type {StackScreenProps} from '../App';

/** ID-1 card ratio: 85.6 mm x 53.98 mm. */
const CARD_RATIO = 85.6 / 54;

/**
 * `ReadPassportAndId` covers all three ICAO layouts — TD1 and TD2 ID cards plus
 * TD3 passports — so a single template handles every document the app accepts.
 * `ReadId` is the card-only variant.
 */
const TEMPLATE_MRZ = 'ReadPassportAndId';

// CameraEnhancer and CaptureVisionRouter are singletons, so the input and the
// filters may only be wired once for the whole app lifetime.
let inputReady = false;

export function CameraScreen({navigation, route}: StackScreenProps<'Camera'>) {
  const side = route.params.side;
  const isMRZ = side === 'back';

  const cameraViewRef = useRef<CameraView>(null);
  const camera = useRef(CameraEnhancer.getInstance()).current;
  const cvr = useRef(CaptureVisionRouter.getInstance()).current;
  const receiverRef = useRef<CapturedResultReceiver | null>(null);
  // Last MRZ text. A capture is only accepted when two frames agree on it.
  const mrzTextRef = useRef('');
  const shutterRef = useRef(false);
  const capturedRef = useRef(false);

  const [pending, setPending] = useState<{image: ImageData; info?: CardInfo} | null>(
    null,
  );
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState('');

  const insets = useSafeAreaInsets();
  const {width, height} = useWindowDimensions();

  const frameWidth = Math.min(width - 56, 340);
  const frameHeight = frameWidth / CARD_RATIO;
  const frameLeft = Math.round((width - frameWidth) / 2);
  const frameTop = Math.round(height * 0.24);

  const handleResult = useCallback(
    (result: CapturedResult) => {
      if (capturedRef.current) {
        return;
      }

      if (isMRZ) {
        const lines = result.recognizedTextLinesResult?.items ?? [];
        const parsed = result.parsedResult?.items?.[0];
        if (!parsed || lines.length === 0) {
          return;
        }
        // The MRZ lines are concatenated into a single text-line item, with the
        // original line breaks preserved inside the text.
        const text = lines.map(line => line.text).join('\n').trim();
        if (text.length < 30) {
          return;
        }
        if (text !== mrzTextRef.current) {
          // First sighting of this read — wait for a frame that confirms it.
          mrzTextRef.current = text;
          return;
        }
        const image = cvr
          .getIntermediateResultManager()
          .getOriginalImage(result.originalImageHashId);
        if (!image || image.width === 0) {
          return;
        }
        capturedRef.current = true;
        setPending({image, info: cardInfoFromMRZ(parsed)});
        return;
      }

      const deskewed = result.processingDocumentResult?.deskewedImageResultItems?.[0];
      if (!deskewed?.imageData) {
        return;
      }
      const stable =
        deskewed.crossVerificationStatus === EnumCrossVerificationStatus.CVS_PASSED;
      if (!stable && !shutterRef.current) {
        return;
      }
      shutterRef.current = false;
      capturedRef.current = true;
      setPending({image: deskewed.imageData});
    },
    [cvr, isMRZ],
  );

  // Bind the view, choose the stream format and open the camera while this
  // screen is focused. The preview handle has to be set before `open()` so that
  // the first frame already has somewhere to render.
  useFocusEffect(
    useCallback(() => {
      if (cameraViewRef.current) {
        camera.setCameraView(cameraViewRef.current);
      }
      camera.setResolution(EnumResolution.Resolution1080P);
      // Keep the full colour frame: `getOriginalImage()` returns whatever the
      // camera feeds the pipeline, and a Y-channel-only stream saves a grey JPEG.
      camera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL);
      camera.open();
      return () => camera.close();
    }, [camera]),
  );

  useEffect(() => {
    let cancelled = false;

    const start = async () => {
      await CameraEnhancer.requestCameraPermission();
      // A previous screen may have left a session running on the singleton.
      await cvr.stopCapturing().catch(() => {});
      if (cancelled) {
        return;
      }

      if (!inputReady) {
        cvr.setInput(camera);
        const filter = new MultiFrameResultCrossFilter();
        // Only report a deskewed card once several frames agree on its corners.
        filter.enableResultCrossVerification(
          EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE,
          true,
        );
        cvr.addFilter(filter);
        inputReady = true;
      }

      receiverRef.current = cvr.addResultReceiver({
        onCapturedResultReceived: handleResult,
      });

      try {
        if (isMRZ) {
          await cvr.startCapturing(TEMPLATE_MRZ);
        } else {
          await cvr.startCapturing(
            EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT,
          );
        }
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message ?? 'Failed to start the scanner.');
        }
      }
    };

    start();

    return () => {
      cancelled = true;
      if (receiverRef.current) {
        cvr.removeResultReceiver(receiverRef.current);
        receiverRef.current = null;
      }
      cvr.stopCapturing().catch(() => {});
      camera.setCameraView(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!pending) {
      return;
    }
    setProcessing(true);
    let cancelled = false;

    (async () => {
      await cvr.stopCapturing().catch(() => {});

      // Both sides are stored as a card-shaped image. The front already comes
      // out of the document template deskewed; the back is the raw frame the
      // MRZ was read from, so it is normalized here. When no card boundary is
      // found the raw frame is kept.
      let image = pending.image;
      if (side === 'back') {
        try {
          const normalized = cvr.capture(
            image,
            EnumPresetTemplate.PT_DETECT_AND_NORMALIZE_DOCUMENT,
          );
          const deskewed =
            normalized?.processingDocumentResult?.deskewedImageResultItems?.[0]
              ?.imageData;
          if (deskewed) {
            image = deskewed;
          }
        } catch {
          // Keep the raw frame; the capture is still usable.
        }
      }

      const base64 = imageDataToBase64(image) ?? '';
      if (cancelled) {
        return;
      }
      CardDraft.applyCapture(side, base64, pending.info);
      navigation.goBack();
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pending]);

  return (
    <View style={styles.container}>
      <CameraView style={StyleSheet.absoluteFill} ref={cameraViewRef} />

      <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
        {/* Dim everything outside the card window. */}
        <View pointerEvents="none">
          <View style={[styles.mask, {top: 0, left: 0, right: 0, height: frameTop}]} />
          <View
            style={[
              styles.mask,
              {top: frameTop + frameHeight, left: 0, right: 0, bottom: 0},
            ]}
          />
          <View
            style={[
              styles.mask,
              {top: frameTop, height: frameHeight, left: 0, width: frameLeft},
            ]}
          />
          <View
            style={[
              styles.mask,
              {top: frameTop, height: frameHeight, right: 0, width: frameLeft},
            ]}
          />
          <View
            style={[
              styles.frame,
              {top: frameTop, left: frameLeft, width: frameWidth, height: frameHeight},
            ]}>
            <View style={[styles.corner, styles.cornerTopLeft]} />
            <View style={[styles.corner, styles.cornerTopRight]} />
            <View style={[styles.corner, styles.cornerBottomLeft]} />
            <View style={[styles.corner, styles.cornerBottomRight]} />
          </View>
        </View>

        {/* Top bar */}
        <View style={[styles.topBar, {paddingTop: insets.top + 8}]}>
          <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={10}>
            <Text style={styles.backText}>‹ Back</Text>
          </TouchableOpacity>
          <Text style={styles.topTitle}>
            {isMRZ ? 'Scan Card Back' : 'Scan Card Front'}
          </Text>
          <View style={styles.topSpacer} />
        </View>

        {/* Bottom bar */}
        <View style={[styles.bottomBar, {paddingBottom: insets.bottom + 24}]}>
          <Text style={styles.hint}>
            {isMRZ
              ? 'Hold the MRZ lines inside the frame — the card is captured automatically'
              : 'Fill the frame with the card — tap the shutter to capture'}
          </Text>

          {processing ? (
            <ActivityIndicator size="large" color="#FFFFFF" />
          ) : isMRZ ? (
            <View style={styles.readingRow}>
              <ActivityIndicator color="#FFFFFF" />
              <Text style={styles.readingText}>Reading MRZ…</Text>
            </View>
          ) : (
            <TouchableOpacity
              activeOpacity={0.8}
              style={styles.shutterOuter}
              onPress={() => {
                shutterRef.current = true;
              }}>
              <View style={styles.shutterInner} />
            </TouchableOpacity>
          )}
        </View>

        {error ? (
          <View style={[styles.errorToast, {top: insets.top + 64}]}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#000000'},
  mask: {position: 'absolute', backgroundColor: 'rgba(0,0,0,0.55)'},
  frame: {position: 'absolute'},
  corner: {
    position: 'absolute',
    width: 28,
    height: 28,
    borderColor: '#FFFFFF',
  },
  cornerTopLeft: {
    top: -2,
    left: -2,
    borderTopWidth: 4,
    borderLeftWidth: 4,
    borderTopLeftRadius: 12,
  },
  cornerTopRight: {
    top: -2,
    right: -2,
    borderTopWidth: 4,
    borderRightWidth: 4,
    borderTopRightRadius: 12,
  },
  cornerBottomLeft: {
    bottom: -2,
    left: -2,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    borderBottomLeftRadius: 12,
  },
  cornerBottomRight: {
    bottom: -2,
    right: -2,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    borderBottomRightRadius: 12,
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingBottom: 12,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  topSpacer: {width: 56},
  backText: {color: '#FFFFFF', fontSize: 16},
  topTitle: {color: '#FFFFFF', fontSize: 16, fontWeight: '700'},
  bottomBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    paddingTop: 16,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  hint: {
    color: '#E2E8F0',
    fontSize: 12,
    textAlign: 'center',
    paddingHorizontal: 32,
    marginBottom: 18,
  },
  shutterOuter: {
    width: 72,
    height: 72,
    borderRadius: 36,
    borderWidth: 4,
    borderColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shutterInner: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#FFFFFF',
  },
  readingRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 18},
  readingText: {color: '#FFFFFF', fontSize: 14, marginLeft: 10},
  errorToast: {
    position: 'absolute',
    left: 20,
    right: 20,
    backgroundColor: '#FEF2F2',
    borderRadius: 10,
    padding: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#EF4444',
  },
  errorText: {color: '#991B1B', fontSize: 13, fontWeight: '600'},
});
