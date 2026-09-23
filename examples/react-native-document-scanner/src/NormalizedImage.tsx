import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Alert,
  Image,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  ImageIO,
  ImageProcessor,
  imageDataToBase64,
} from 'dynamsoft-capture-vision-react-native';
import {
  CachesDirectoryPath,
  exists,
  stat,
  TemporaryDirectoryPath,
} from 'react-native-fs';
import Share from 'react-native-share';
import {StackNavigation} from './App.tsx';
import {useFocusEffect} from '@react-navigation/native';

const ColorMode = {
  color: 'color',
  grayscale: 'grayscale',
  binary: 'binary',
};

/**
 * The formats the scanned document can be exported to.
 *
 * `ImageIO.saveToFile` infers the encoder from the file extension (the native
 * `ImageIO` supports PNG and PDF), so the extension is what actually selects
 * the output format. The mime type is what the share sheet advertises so that
 * receiving apps can filter themselves in.
 */
const ExportFormat = {
  png: {ext: 'png', label: 'PNG Image', mime: 'image/png'},
  pdf: {ext: 'pdf', label: 'PDF Document', mime: 'application/pdf'},
} as const;

type ExportFormatKey = keyof typeof ExportFormat;

const TABS = [
  {icon: '✏️', label: 'Edit', color: '#2563EB'},
  {icon: '🎨', label: 'Color', color: '#7C3AED'},
  {icon: '⬆️', label: 'Export', color: '#16A34A'},
];

export const NormalizedImage = ({navigation}: StackNavigation) => {
  const [base64, setBase64] = useState('');
  // Guards against overlapping exports (e.g. a double-tap on the alert button),
  // which would race on the same native ImageIO buffer.
  const isExporting = useRef(false);
  const insets = useSafeAreaInsets();

  // Override the hardware/header back button so it goes to Home, skipping Scanner.
  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (e: any) => {
      // Only intercept default back actions (not programmatic navigations like Editor).
      if (e.data.action.type === 'GO_BACK' || e.data.action.type === 'POP') {
        e.preventDefault();
        navigation.navigate('Home');
      }
    });
    return unsubscribe;
  }, [navigation]);

  useFocusEffect(
    useCallback(() => {
      global.showingImage = global.deskewedImage;
      setBase64(imageDataToBase64(global.showingImage) ?? '');
    }, []),
  );

  useEffect(() => {
    return () => {
      // Release showingImage first only if it's a distinct object from deskewedImage,
      // otherwise we'd double-free the same native buffer.
      if (global.showingImage && global.showingImage !== global.deskewedImage) {
        global.showingImage.release();
      }
      global.deskewedImage?.release();
      global.originalImage?.release();
    };
  }, []);

  const changeColorMode = (mode: string) => {
    if (global.showingImage && global.showingImage !== global.deskewedImage) {
      global.showingImage.release();
    }
    switch (mode) {
      case ColorMode.color:
        global.showingImage = global.deskewedImage;
        break;
      case ColorMode.grayscale:
        global.showingImage = new ImageProcessor().convertToGray(global.deskewedImage) ?? global.deskewedImage;
        break;
      case ColorMode.binary:
        global.showingImage =
          new ImageProcessor().convertToBinaryLocal(
            global.deskewedImage,
            /*blockSize = */ 0,
            /*compensation = */ 10,
            /*invert = */ false,
          ) ?? global.deskewedImage;
        break;
    }
    setBase64(imageDataToBase64(global.showingImage) ?? '');
  };

  /**
   * Encodes the currently displayed document with `ImageIO`, then hands the
   * resulting file to the system share sheet.
   *
   * The bytes are written to a file first because both platforms share files
   * through a URI rather than by passing a buffer around — and it means nothing
   * is lost if the user dismisses the sheet.
   */
  const exportDocument = async (key: ExportFormatKey) => {
    if (isExporting.current) {
      return;
    }
    if (!global.showingImage) {
      Alert.alert('Nothing to Export', 'Scan a document before exporting.');
      return;
    }

    isExporting.current = true;
    const {ext, label, mime} = ExportFormat[key];
    // react-native-share exposes Android files through the FileProvider it
    // declares, and that provider only covers the *internal* cache directory.
    // Writing anywhere else makes getUriForFile() throw and the sheet never
    // opens. iOS will share any temporary file path.
    const directory =
      Platform.OS === 'ios' ? TemporaryDirectoryPath : CachesDirectoryPath;
    const filename = `document_${Date.now()}`;
    const savedPath = `${directory}/${filename}.${ext}`;
    let saved = false;

    try {
      new ImageIO().saveToFile(global.showingImage, savedPath, true);

      // `saveToFile` returns void and the native layer can fail without
      // surfacing an error, so verify the file was actually produced instead
      // of handing a missing path to the share sheet.
      if (!(await exists(savedPath))) {
        throw new Error(`The ${label} could not be written to:\n${savedPath}`);
      }
      const {size} = await stat(savedPath);
      if (!size) {
        throw new Error(`The ${label} was created but is empty:\n${savedPath}`);
      }
      saved = true;

      await Share.open({
        url: `file://${savedPath}`,
        type: mime,
        filename,
        failOnCancel: false, // dismissing the sheet is a normal outcome, not a failure
      });
    } catch (e: any) {
      console.error(`Export ${key} failed: ` + e.message);
      Alert.alert(
        'Export Failed',
        saved ? `${e.message}\n\nThe document was saved to:\n${savedPath}` : e.message,
      );
    } finally {
      isExporting.current = false;
    }
  };

  const onTabPress = (index: number) => {
    switch (index) {
      case 0:
        navigation.navigate('Editor');
        break;
      case 1:
        Alert.alert(
          'Select Color Mode',
          '',
          [ColorMode.color, ColorMode.grayscale, ColorMode.binary].map(mode => ({
            text: mode.charAt(0).toUpperCase() + mode.slice(1),
            onPress: () => changeColorMode(mode),
          })),
          {cancelable: true},
        );
        break;
      case 2:
        Alert.alert(
          'Export & Share',
          'Choose a format — the share sheet opens once the file is ready.',
          (Object.keys(ExportFormat) as ExportFormatKey[]).map(key => ({
            text: ExportFormat[key].label,
            onPress: () => exportDocument(key),
          })),
          {cancelable: true},
        );
        break;
    }
  };

  return (
    <View style={styles.container}>
      {/* Document image on dark background */}
      <View style={styles.imageContainer}>
        {base64 ? (
          <Image
            source={{uri: 'data:image/png;base64,' + base64}}
            style={styles.image}
            resizeMode="contain"
          />
        ) : null}
      </View>

      {/* Bottom tab bar */}
      <View style={[styles.tabBar, {paddingBottom: insets.bottom + 8}]}>
        {TABS.map(({icon, label, color}, index) => (
          <TouchableOpacity
            key={index}
            style={styles.tabItem}
            activeOpacity={0.7}
            onPress={() => onTabPress(index)}>
            <View style={[styles.tabIconBg, {backgroundColor: color + '18'}]}>
              <Text style={styles.tabIcon}>{icon}</Text>
            </View>
            <Text style={[styles.tabLabel, {color}]}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  imageContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  image: {
    width: '100%',
    height: '100%',
  },
  // ─── Tab bar ────────────────────────────────────────────────────────────────
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E2E8F0',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: -2},
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 10,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    paddingBottom: 4,
  },
  tabIconBg: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  tabIcon: {fontSize: 22},
  tabLabel: {fontSize: 12, fontWeight: '600'},
});
