import React, {useEffect, useRef} from 'react';
import {Alert, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  EnumDrawingLayerId,
  ImageData,
  ImageEditorView,
  ImageProcessor,
} from 'dynamsoft-capture-vision-react-native';
import {StackNavigation} from './App.tsx';

const PRIMARY = '#2563EB';

export function Editor({navigation}: StackNavigation) {
  const editorView = useRef<ImageEditorView>(null);
  const insets = useSafeAreaInsets();

  useEffect(() => {
    // Dep array is intentionally empty: the ref is populated by mount time
    // and ImageEditorView ref objects are stable — changing them won't re-run this.
    if (editorView.current) {
      editorView.current.setOriginalImage(global.originalImage);
      editorView.current.setQuads([global.sourceDeskewQuad], EnumDrawingLayerId.DDN_LAYER_ID);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const getSelectedQuadAndDeskew = async (): Promise<ImageData | null | undefined> => {
    if (!editorView.current) {
      return null;
    }
    const quad = await editorView.current.getSelectedQuad().catch(e => {
      console.error('getSelectedQuad error: ' + e.message);
      return null;
    });
    if (quad) {
      global.sourceDeskewQuad = quad;
      return new ImageProcessor().cropAndDeskewImage(global.originalImage, quad);
    } else {
      Alert.alert('No selection', 'Please select a quad to confirm.');
      return null;
    }
  };

  return (
    <ImageEditorView style={styles.fullScreen} ref={editorView}>
      <View style={[styles.bottomBar, {paddingBottom: insets.bottom + 12}]}>
        <Text style={styles.hint}>Drag the corners to adjust the document boundary</Text>
        <TouchableOpacity
          style={styles.confirmButton}
          activeOpacity={0.85}
          onPress={async () => {
            const deskewedImage = await getSelectedQuadAndDeskew();
            if (deskewedImage) {
              if (global.deskewedImage) {
                global.deskewedImage.release();
              }
              global.deskewedImage = deskewedImage;
              navigation.pop(1);
            }
          }}>
          <Text style={styles.confirmButtonText}>✓  Confirm Crop</Text>
        </TouchableOpacity>
      </View>
    </ImageEditorView>
  );
}

const styles = StyleSheet.create({
  fullScreen: {flex: 1},
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 20,
    paddingTop: 14,
    backgroundColor: '#FFFFFF',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E2E8F0',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: -2},
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 10,
  },
  hint: {
    fontSize: 13,
    color: '#64748B',
    textAlign: 'center',
    marginBottom: 12,
  },
  confirmButton: {
    backgroundColor: PRIMARY,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: 'center',
    shadowColor: PRIMARY,
    shadowOffset: {width: 0, height: 3},
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 4,
  },
  confirmButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
});

