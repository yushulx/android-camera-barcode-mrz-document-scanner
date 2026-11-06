import React, {useCallback, useEffect, useState} from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {NavigationContainer} from '@react-navigation/native';
import {
  createNativeStackNavigator,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import {LicenseManager} from 'dynamsoft-capture-vision-react-native';
import {HomeScreen} from './screens/HomeScreen';
import {CardScreen} from './screens/CardScreen';
import {CameraScreen} from './screens/CameraScreen';
import type {CardSide} from './models/ScannedIDCard';

/**
 * Trial license. It is validated against Dynamsoft's license server at startup,
 * so the device needs a network connection to run the sample. Request your own
 * key at https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform
 */
const LICENSE_KEY =
  'DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==';

export type RootStackParamList = {
  Home: undefined;
  Card: {cardKey?: string};
  Camera: {side: CardSide};
};

export type StackScreenProps<T extends keyof RootStackParamList> =
  NativeStackScreenProps<RootStackParamList, T>;

const Stack = createNativeStackNavigator<RootStackParamList>();

const PRIMARY = '#2563EB';

function Splash() {
  return (
    <View style={styles.centered}>
      <ActivityIndicator size="large" color={PRIMARY} />
      <Text style={styles.centeredText}>Initializing the scanner…</Text>
    </View>
  );
}

interface LicenseErrorProps {
  message: string;
  onRetry: () => void;
}

function LicenseError({message, onRetry}: LicenseErrorProps) {
  return (
    <View style={styles.centered}>
      <Text style={styles.errorTitle}>License initialization failed</Text>
      <Text style={styles.errorBody}>{message}</Text>
      <TouchableOpacity style={styles.retryButton} onPress={onRetry}>
        <Text style={styles.retryLabel}>Retry</Text>
      </TouchableOpacity>
    </View>
  );
}

function App(): React.JSX.Element {
  const [licenseState, setLicenseState] = useState<'pending' | 'ready' | 'failed'>(
    'pending',
  );
  const [licenseError, setLicenseError] = useState('');

  const initLicense = useCallback(async () => {
    setLicenseState('pending');
    try {
      await LicenseManager.initLicense(LICENSE_KEY);
      setLicenseState('ready');
    } catch (e: any) {
      setLicenseError(e?.message ?? String(e));
      setLicenseState('failed');
    }
  }, []);

  useEffect(() => {
    initLicense();
  }, [initLicense]);

  if (licenseState === 'pending') {
    return <Splash />;
  }

  if (licenseState === 'failed') {
    return <LicenseError message={licenseError} onRetry={initLicense} />;
  }

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
            name="Card"
            component={CardScreen}
            options={{
              title: 'Card',
              headerStyle: {backgroundColor: PRIMARY},
              headerTintColor: '#FFFFFF',
              headerTitleStyle: {fontWeight: '700'},
            }}
          />
          <Stack.Screen
            name="Camera"
            component={CameraScreen}
            options={{headerShown: false}}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
    backgroundColor: '#F1F5F9',
  },
  centeredText: {marginTop: 14, fontSize: 15, color: '#475569'},
  errorTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#B91C1C',
    marginBottom: 8,
  },
  errorBody: {
    fontSize: 13,
    color: '#7F1D1D',
    textAlign: 'center',
    lineHeight: 19,
  },
  retryButton: {
    marginTop: 20,
    paddingHorizontal: 28,
    paddingVertical: 12,
    borderRadius: 10,
    backgroundColor: PRIMARY,
  },
  retryLabel: {color: '#FFFFFF', fontSize: 15, fontWeight: '600'},
});

export default App;
