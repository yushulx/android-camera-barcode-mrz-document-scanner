import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {createNativeStackNavigator, NativeStackScreenProps} from '@react-navigation/native-stack';
import {NavigationContainer} from '@react-navigation/native';
import {Quadrilateral, ImageData, LicenseManager} from 'dynamsoft-capture-vision-react-native';
import {Scanner} from './Scanner.tsx';
import {Editor} from './Editor.tsx';
import {NormalizedImage} from './NormalizedImage.tsx';

declare global {
  var originalImage: ImageData;
  var deskewedImage: ImageData;
  var showingImage: ImageData;
  var sourceDeskewQuad: Quadrilateral;
}

export type ScreenNames = ['Home', 'Scanner', 'Editor', 'NormalizedImage'];
export type RootStackParamList = Record<ScreenNames[number], undefined>;
export type StackNavigation = NativeStackScreenProps<RootStackParamList>;

const Stack = createNativeStackNavigator<RootStackParamList>();

const PRIMARY = '#2563EB';

function App(): React.JSX.Element {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <Stack.Navigator initialRouteName="Home">
          <Stack.Screen
            name="Home"
            component={HomeScreen}
            options={{headerShown: false}}
          />
          <Stack.Screen
            name="Scanner"
            component={Scanner}
            options={{headerShown: false}}
          />
          <Stack.Screen
            name="Editor"
            component={Editor}
            options={{
              title: 'Adjust & Crop',
              headerStyle: {backgroundColor: PRIMARY},
              headerTintColor: '#fff',
              headerTitleStyle: {fontWeight: '700', fontSize: 17},
            }}
          />
          <Stack.Screen
            name="NormalizedImage"
            component={NormalizedImage}
            options={{
              title: 'Review & Export',
              headerStyle: {backgroundColor: PRIMARY},
              headerTintColor: '#fff',
              headerTitleStyle: {fontWeight: '700', fontSize: 17},
            }}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}

function HomeScreen({navigation}: StackNavigation) {
  const [error, setError] = useState<string | null>(null);
  const [licenseReady, setLicenseReady] = useState(false);

  // Initialize the license once on mount.
  // The license string here is a trial license. Note that network connection is required for this license to work.
  // You can request an extension via the following link: https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform
  useEffect(() => {
    LicenseManager.initLicense('DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==')
      .then(() => setLicenseReady(true))
      .catch(e => {
        console.error('Init license failed: ' + e.message);
        setError('License initialization failed.\n' + e.message);
        setLicenseReady(true);
      });
  }, []);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom']}>
      {/* Hero */}
      <View style={styles.hero}>
        <View style={styles.iconCircle}>
          <Text style={styles.iconEmoji}>📄</Text>
        </View>
        <Text style={styles.appTitle}>Document Scanner</Text>
        <Text style={styles.appSubtitle}>
          Scan, adjust, and export documents in seconds
        </Text>
      </View>

      {/* Feature list */}
      <View style={styles.featureCard}>
        {[
          {icon: '📐', text: 'Auto-detect document edges'},
          {icon: '✂️', text: 'Manual crop & perspective correction'},
          {icon: '🎨', text: 'Colour, grayscale or binary output'},
          {icon: '💾', text: 'Export as high-quality PNG'},
        ].map(({icon, text}, i) => (
          <View key={i} style={[styles.featureRow, i > 0 && styles.featureRowBorder]}>
            <Text style={styles.featureIcon}>{icon}</Text>
            <Text style={styles.featureText}>{text}</Text>
          </View>
        ))}
      </View>

      {/* CTA */}
      <View style={styles.ctaContainer}>
        {licenseReady ? (
          <TouchableOpacity
            style={styles.ctaButton}
            activeOpacity={0.85}
            onPress={() => navigation.navigate('Scanner')}>
            <Text style={styles.ctaButtonText}>📷  Scan a Document</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.loadingBox}>
            <ActivityIndicator size="large" color={PRIMARY} />
            <Text style={styles.loadingText}>Initializing SDK…</Text>
          </View>
        )}
        {error ? (
          <View style={styles.errorCard}>
            <Text style={styles.errorTitle}>⚠️  License Error</Text>
            <Text style={styles.errorBody}>{error}</Text>
          </View>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F1F5F9',
  },
  // ─── Hero ───────────────────────────────────────────────────────────────────
  hero: {
    alignItems: 'center',
    paddingTop: 48,
    paddingBottom: 32,
    paddingHorizontal: 24,
  },
  iconCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#DBEAFE',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    shadowColor: PRIMARY,
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 6,
  },
  iconEmoji: {fontSize: 40},
  appTitle: {
    fontSize: 26,
    fontWeight: '800',
    color: '#0F172A',
    letterSpacing: 0.3,
    marginBottom: 8,
  },
  appSubtitle: {
    fontSize: 15,
    color: '#64748B',
    textAlign: 'center',
    lineHeight: 22,
  },
  // ─── Features ───────────────────────────────────────────────────────────────
  featureCard: {
    marginHorizontal: 20,
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    paddingHorizontal: 20,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
  },
  featureRowBorder: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E2E8F0',
  },
  featureIcon: {fontSize: 20, marginRight: 14},
  featureText: {fontSize: 15, color: '#334155', flex: 1},
  // ─── CTA ─────────────────────────────────────────────────────────────────────
  ctaContainer: {
    flex: 1,
    justifyContent: 'flex-end',
    paddingHorizontal: 20,
    paddingBottom: 24,
  },
  ctaButton: {
    backgroundColor: PRIMARY,
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    shadowColor: PRIMARY,
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 6,
  },
  ctaButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  loadingBox: {
    alignItems: 'center',
    paddingVertical: 20,
    gap: 12,
  },
  loadingText: {fontSize: 15, color: '#64748B'},
  errorCard: {
    marginTop: 16,
    backgroundColor: '#FEF2F2',
    borderRadius: 12,
    padding: 14,
    borderLeftWidth: 4,
    borderLeftColor: '#EF4444',
  },
  errorTitle: {fontSize: 14, fontWeight: '700', color: '#DC2626', marginBottom: 4},
  errorBody: {fontSize: 13, color: '#991B1B', lineHeight: 18},
});

export default App;
