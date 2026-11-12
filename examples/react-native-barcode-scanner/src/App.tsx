/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import {
  CameraEnhancer,
  CameraView,
  CaptureVisionRouter,
  EnumPresetTemplate,
  LicenseManager,
} from 'dynamsoft-capture-vision-react-native';
import React, { useEffect, useRef, useState } from 'react';
import { Alert, AppState, SafeAreaView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

const License = 'DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==';
LicenseManager.initLicense(License).catch(e => {
  Alert.alert('License error', e.message);
});

// Type for barcode overlay data
interface BarcodeOverlay {
  id: string;
  text: string;
  formatString: string;
  x: number;
  y: number;
  corners: Array<{ x: number, y: number }>; // Add corners for contour drawing
}

// Component to draw barcode contour using the four corners
const BarcodeContour: React.FC<{ corners: Array<{ x: number, y: number }> }> = ({ corners }) => {
  if (corners.length !== 4) {
    return null;
  }

  return (
    <>
      {/* Draw lines between corners to form the contour */}
      {corners.map((corner, index) => {
        const nextCorner = corners[(index + 1) % 4];
        const lineWidth = 3;

        // Calculate line properties
        const deltaX = nextCorner.x - corner.x;
        const deltaY = nextCorner.y - corner.y;
        const length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        const angle = Math.atan2(deltaY, deltaX) * (180 / Math.PI);

        return (
          <View
            key={index}
            style={[
              styles.contourLine,
              {
                left: corner.x,
                top: corner.y - lineWidth / 2,
                width: length,
                height: lineWidth,
                transform: [{ rotate: `${angle}deg` }],
              },
            ]}
          />
        );
      })}

      {/* Draw corner dots */}
      {corners.map((corner, index) => (
        <View
          key={`dot-${index}`}
          style={[
            styles.cornerDot,
            {
              left: corner.x - 4,
              top: corner.y - 4,
            },
          ]}
        />
      ))}
    </>
  );
};

function App(): React.JSX.Element {
  const cameraView = useRef<CameraView>(null!);
  const cvr = CaptureVisionRouter.getInstance();
  const camera = CameraEnhancer.getInstance();

  // State to hold detected barcodes for overlay
  const [barcodeOverlays, setBarcodeOverlays] = useState<BarcodeOverlay[]>([]);

  // Ref to store the last overlay clear timeout
  const clearTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // useWindowDimensions updates on rotation. The receiver below is registered
  // once, so it must read the CURRENT layout and orientation through refs —
  // capturing Dimensions.get('window') values in the effect closure leaves
  // overlays misaligned after a rotation or an onLayout change.
  const { width: windowWidth, height: windowHeight } = useWindowDimensions();
  const viewRef = useRef({ width: windowWidth, height: windowHeight });
  const orientationRef = useRef<'portrait' | 'landscape'>(
    windowWidth > windowHeight ? 'landscape' : 'portrait',
  );

  useEffect(() => {
    orientationRef.current = windowWidth > windowHeight ? 'landscape' : 'portrait';
    viewRef.current = { width: windowWidth, height: windowHeight };
    console.log(`Screen dimensions: ${windowWidth}x${windowHeight}`);
  }, [windowWidth, windowHeight]);

  useEffect(() => {
    // Request camera permission once
    CameraEnhancer.requestCameraPermission();

    // Configure router, camera and view
    cvr.setInput(camera);
    camera.setCameraView(cameraView.current);

    // Barcode result handler
    const receiver = cvr.addResultReceiver({
      onDecodedBarcodesReceived: ({ items }) => {
        if (items?.length) {
          // Create overlay data for each detected barcode
          // Read live values: a rotation changes both the image basis and
          // the preview dimensions, but this receiver is never re-registered.
          const { width: viewWidth, height: viewHeight } = viewRef.current;
          const isLandscape = orientationRef.current === 'landscape';
          const cameraResolutionWidth = isLandscape ? 1920 : 1080;
          const cameraResolutionHeight = isLandscape ? 1080 : 1920;

          // Math.max fills the CameraView (aspect fill) and crops the excess.
          const scaleFactor = Math.max(
            viewWidth / cameraResolutionWidth,
            viewHeight / cameraResolutionHeight,
          );
          const cropOffsetX = (cameraResolutionWidth * scaleFactor - viewWidth) / 2;
          const cropOffsetY = (cameraResolutionHeight * scaleFactor - viewHeight) / 2;

          const overlays: BarcodeOverlay[] = items.map((item) => {

            // Scale all four corner points and adjust for crop offset
            const scaledCorners = item.location.points.map(point => ({
              x: (point.x * scaleFactor) - cropOffsetX,
              y: (point.y * scaleFactor) - cropOffsetY,
            }));

            // Calculate center from all four scaled corners for text positioning
            const centerX = scaledCorners.reduce((sum, point) => sum + point.x, 0) / 4;
            const centerY = scaledCorners.reduce((sum, point) => sum + point.y, 0) / 4;

            // Create a stable ID based on barcode content and rough position
            const stableId = `${item.text}-${item.formatString}-${Math.round(centerX / 10) * 10}-${Math.round(centerY / 10) * 10}`;

            return {
              id: stableId, // Stable ID based on content and position
              text: item.text,
              formatString: item.formatString,
              x: centerX,
              y: centerY,
              corners: scaledCorners,
            };
          });

          // Update state with new overlays
          setBarcodeOverlays(overlays);

          // Clear any existing timeout
          if (clearTimeoutRef.current) {
            clearTimeout(clearTimeoutRef.current);
          }

          // Clear overlays after 2 seconds of no new detections
          clearTimeoutRef.current = setTimeout(() => {
            setBarcodeOverlays([]);
          }, 2000);
        }
      },
    });

    // Helper to start camera + scanning
    const startScanning = () => {
      console.log('Starting...');
      camera.open();
      cvr.startCapturing(EnumPresetTemplate.PT_READ_BARCODES)
        .catch(e => Alert.alert('Start error', e.message));
    };

    // Helper to stop camera + scanning
    const stopScanning = () => {
      console.log('Stopping...');
      cvr.stopCapturing();
      camera.close();
    };

    // Initial start when component mounts
    startScanning();

    // Listen to AppState changes
    const sub = AppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        startScanning();
      } else if (nextState.match(/inactive|background/)) {
        stopScanning();
      }
    });

    // Cleanup on unmount
    return () => {
      sub.remove();
      stopScanning();
      cvr.removeResultReceiver(receiver);
      if (clearTimeoutRef.current) {
        clearTimeout(clearTimeoutRef.current);
      }
    };
  }, [cvr, camera]);

  return (
    <SafeAreaView style={StyleSheet.absoluteFill}>
      <CameraView
        style={StyleSheet.absoluteFill}
        ref={cameraView}
        onLayout={(event) => {
          const { width, height } = event.nativeEvent.layout;
          viewRef.current = { width, height };
          console.log(`Camera view dimensions: ${width}x${height}`);
        }}
      />

      {/* Barcode contours */}
      {barcodeOverlays.map((overlay) => (
        <BarcodeContour key={`contour-${overlay.id}`} corners={overlay.corners} />
      ))}

      {/* Barcode text overlays */}
      {barcodeOverlays.map((overlay) => (
        <View
          key={overlay.id}
          style={[
            styles.barcodeOverlay,
            {
              left: overlay.x - 75, // Offset to center the text
              top: overlay.y - 40,  // Offset to position above the barcode
            },
          ]}
        >
          <Text style={styles.barcodeText}>{overlay.text}</Text>
          <Text style={styles.formatText}>{overlay.formatString}</Text>
        </View>
      ))}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  barcodeOverlay: {
    position: 'absolute',
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    borderRadius: 8,
    padding: 8,
    alignItems: 'center',
    minWidth: 150,
    maxWidth: 250,
  },
  barcodeText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 2,
  },
  formatText: {
    color: '#00FF00',
    fontSize: 10,
    textAlign: 'center',
  },
  contourLine: {
    position: 'absolute',
    backgroundColor: '#00FF00',
    transformOrigin: 'left center',
  },
  cornerDot: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#FF0000',
  },
});

export default App;


