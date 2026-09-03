import { StatusBar } from 'expo-status-bar';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import ExpoDynamsoftBarcodeScanner, {
  BarcodeResult,
} from './modules/expo-dynamsoft-barcode-scanner/src/ExpoDynamsoftBarcodeScannerModule';

type SourceMode = 'camera' | 'file';

export default function App() {
  const [mode, setMode] = useState<SourceMode>('camera');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<BarcodeResult[]>([]);

  const scan = useCallback(async () => {
    setBusy(true);
    setError(null);
    setResults([]);
    try {
      const license = await ExpoDynamsoftBarcodeScanner.initLicense();
      if (!license.success) {
        setError(`License failed: ${license.message}`);
        return;
      }
      const outcome =
        mode === 'camera'
          ? await ExpoDynamsoftBarcodeScanner.startScan()
          : await ExpoDynamsoftBarcodeScanner.scanFromGallery();
      setResults(outcome.results);
    } catch (e: any) {
      const message: string = e?.message ?? String(e);
      if (!/cancel/i.test(message)) {
        setError(message);
      }
    } finally {
      setBusy(false);
    }
  }, [mode]);

  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <Text style={styles.title}>Dynamsoft Barcode Scanner</Text>

      <View style={styles.segment}>
        {(['camera', 'file'] as SourceMode[]).map((m) => (
          <Pressable
            key={m}
            style={[styles.segmentItem, mode === m && styles.segmentItemActive]}
            onPress={() => setMode(m)}>
            <Text style={[styles.segmentText, mode === m && styles.segmentTextActive]}>
              {m === 'camera' ? 'Camera' : 'Image File'}
            </Text>
          </Pressable>
        ))}
      </View>

      <Pressable
        style={[styles.scanButton, busy && styles.scanButtonDisabled]}
        onPress={scan}
        disabled={busy}>
        {busy ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.scanButtonText}>
            {mode === 'camera' ? 'Scan with Camera' : 'Pick an Image'}
          </Text>
        )}
      </Pressable>

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <Text style={styles.count}>
        {results.length === 0 ? 'No barcode decoded yet' : `${results.length} barcode(s) found`}
      </Text>

      <ScrollView style={styles.resultList}>
        {results.map((r, i) => (
          <View key={`${r.text}-${i}`} style={styles.resultCard}>
            <Text style={styles.resultFormat}>{r.formatString}</Text>
            <Text style={styles.resultText}>{r.text}</Text>
            {r.points.length === 4 ? (
              <Text style={styles.resultPoints}>
                corners: {r.points.map((p) => `(${Math.round(p.x)}, ${Math.round(p.y)})`).join(' ')}
              </Text>
            ) : null}
          </View>
        ))}
      </ScrollView>

      <Text style={styles.footer}>
        Expo {Platform.OS === 'ios' ? 'iOS' : 'Android'} native bridge — Dynamsoft Capture Vision
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingTop: 64,
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 16,
  },
  segment: {
    flexDirection: 'row',
    backgroundColor: '#eef0f3',
    borderRadius: 10,
    padding: 4,
    marginBottom: 16,
  },
  segmentItem: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
  },
  segmentItemActive: {
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 3,
    shadowOffset: { width: 0, height: 1 },
  },
  segmentText: {
    color: '#555',
    fontSize: 14,
    fontWeight: '500',
  },
  segmentTextActive: {
    color: '#111',
    fontWeight: '700',
  },
  scanButton: {
    backgroundColor: '#ff8f0d',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
  },
  scanButtonDisabled: {
    opacity: 0.6,
  },
  scanButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  error: {
    color: '#c62828',
    marginTop: 12,
    textAlign: 'center',
  },
  count: {
    marginTop: 16,
    marginBottom: 8,
    color: '#666',
    fontSize: 13,
  },
  resultList: {
    flex: 1,
  },
  resultCard: {
    backgroundColor: '#f7f8fa',
    borderRadius: 10,
    padding: 12,
    marginBottom: 10,
  },
  resultFormat: {
    color: '#ff8f0d',
    fontWeight: '700',
    fontSize: 13,
    marginBottom: 4,
  },
  resultText: {
    color: '#111',
    fontSize: 15,
  },
  resultPoints: {
    color: '#888',
    fontSize: 12,
    marginTop: 6,
  },
  footer: {
    color: '#aaa',
    fontSize: 11,
    textAlign: 'center',
    paddingVertical: 10,
  },
});
