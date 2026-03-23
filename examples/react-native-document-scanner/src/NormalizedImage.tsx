import React, {useCallback, useEffect, useState} from 'react';
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
  ExternalCachesDirectoryPath,
  TemporaryDirectoryPath,
} from 'react-native-fs';
import {StackNavigation} from './App.tsx';
import {useFocusEffect, useNavigation} from '@react-navigation/native';
import {useCallback as useCallbackHook} from 'react';

const ColorMode = {
  color: 'color',
  grayscale: 'grayscale',
  binary: 'binary',
};

const TABS = [
  {icon: '✏️', label: 'Edit', color: '#2563EB'},
  {icon: '🎨', label: 'Color', color: '#7C3AED'},
  {icon: '⬆️', label: 'Export', color: '#16A34A'},
];

export const NormalizedImage = ({navigation}: StackNavigation) => {
  const [base64, setBase64] = useState('');
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
        try {
          const imageIO = new ImageIO();
          const savedPath =
            (Platform.OS === 'ios'
              ? TemporaryDirectoryPath
              : ExternalCachesDirectoryPath) + `/document_${Date.now()}.png`;
          imageIO.saveToFile(global.showingImage, savedPath, true);
          Alert.alert('Saved ✓', 'Image saved to:\n' + savedPath);
        } catch (e: any) {
          console.error('Export failed: ' + e.message);
          Alert.alert('Export Failed', e.message);
        }
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
