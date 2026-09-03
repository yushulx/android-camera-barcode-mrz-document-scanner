import { StatusBar } from 'expo-status-bar';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import ExpoDynamsoftMrzScanner, {
  IdScanResult,
} from './modules/expo-dynamsoft-mrz-scanner/src/ExpoDynamsoftMrzScannerModule';

type SourceMode = 'camera' | 'file';

/** Display order of the parsed MRZ fields (labels mirror the Ionic example). */
const FIELD_ROWS: Array<[keyof IdScanResult['fields'], string]> = [
  ['name', 'Name'],
  ['sex', 'Sex'],
  ['age', 'Age'],
  ['documentNumber', 'Document Number'],
  ['issuingState', 'Issuing State'],
  ['nationality', 'Nationality'],
  ['dateOfBirth', 'Date of Birth'],
  ['dateOfExpiry', 'Date of Expiry'],
];

export default function App() {
  const [result, setResult] = useState<IdScanResult | null>(null);

  if (result) {
    return <ResultView result={result} onBack={() => setResult(null)} />;
  }
  return <HomeView onResult={setResult} />;
}

// MARK: - Home (camera / image file source)

function HomeView({ onResult }: { onResult: (r: IdScanResult) => void }) {
  const [mode, setMode] = useState<SourceMode>('camera');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const scan = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const license = await ExpoDynamsoftMrzScanner.initLicense();
      if (!license.success) {
        setError(`License failed: ${license.message}`);
        return;
      }
      const outcome =
        mode === 'camera'
          ? await ExpoDynamsoftMrzScanner.startScan()
          : await ExpoDynamsoftMrzScanner.scanFromGallery();
      onResult(outcome);
    } catch (e: any) {
      const message: string = e?.message ?? String(e);
      if (!/cancel/i.test(message)) {
        setError(message);
      }
    } finally {
      setBusy(false);
    }
  }, [mode, onResult]);

  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <Text style={styles.title}>MRZ Document Scanner</Text>

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

      <View style={styles.hintCard}>
        <Text style={styles.hintTitle}>How it works</Text>
        <Text style={styles.hintText}>
          Point the camera at the MRZ zone of a passport (bottom of the bio-data page) or an ID
          card. The document quad, the MRZ lines and the portrait zone are drawn on the preview.
          When a document is detected the Confirm button becomes enabled — the result page shows
          the parsed fields plus the perspective-corrected document and the cropped portrait.
        </Text>
      </View>
    </View>
  );
}

// MARK: - Result page

function ResultView({ result, onBack }: { result: IdScanResult; onBack: () => void }) {
  const fields = result.fields;

  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <View style={styles.navBar}>
        <Pressable style={styles.backButton} onPress={onBack} hitSlop={8}>
          <Text style={styles.backButtonText}>←</Text>
        </Pressable>
        <Text style={styles.navTitle}>Scan Result</Text>
        <View style={styles.backButton} />
      </View>

      <ScrollView style={styles.resultScroll} contentContainerStyle={styles.resultContent}>
        {result.documentImageBase64 ? (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Detected document</Text>
            <Text style={styles.cardSubtitle}>
              Card boundary detected &amp; perspective corrected on-device
            </Text>
            <View style={styles.mediaFrameDoc}>
              <Image
                source={{ uri: result.documentImageBase64 }}
                style={styles.resultImage}
                resizeMode="contain"
              />
            </View>
          </View>
        ) : null}

        {result.portraitBase64 ? (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Detected portrait</Text>
            <Text style={styles.cardSubtitle}>Extracted from the auxiliary portrait zone</Text>
            <View style={styles.mediaFramePortrait}>
              <Image
                source={{ uri: result.portraitBase64 }}
                style={styles.resultImage}
                resizeMode="contain"
              />
            </View>
          </View>
        ) : null}

        <View style={styles.card}>
          <View style={styles.fieldsHeader}>
            <Text style={styles.cardTitle}>{fields.documentType ?? 'Document'}</Text>
            {fields.documentNumber && fields.documentNumber !== '—' ? (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{fields.documentNumber}</Text>
              </View>
            ) : null}
          </View>
          {FIELD_ROWS.map(([key, label]) => (
            <View key={key} style={styles.fieldRow}>
              <Text style={styles.fieldLabel}>{label}</Text>
              <Text style={styles.fieldValue}>{fields[key] ?? '—'}</Text>
            </View>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

// MARK: - Styles

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
  hintCard: {
    backgroundColor: '#f7f8fa',
    borderRadius: 10,
    padding: 14,
    marginTop: 20,
  },
  hintTitle: {
    fontWeight: '700',
    fontSize: 14,
    marginBottom: 6,
    color: '#333',
  },
  hintText: {
    fontSize: 13,
    lineHeight: 19,
    color: '#666',
  },
  navBar: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  backButton: {
    width: 40,
    alignItems: 'center',
  },
  backButtonText: {
    fontSize: 24,
    color: '#ff8f0d',
    fontWeight: '700',
  },
  navTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 17,
    fontWeight: '700',
  },
  resultScroll: {
    flex: 1,
  },
  resultContent: {
    paddingBottom: 32,
  },
  card: {
    backgroundColor: '#f7f8fa',
    borderRadius: 12,
    padding: 14,
    marginBottom: 14,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#111',
  },
  cardSubtitle: {
    fontSize: 12,
    color: '#888',
    marginTop: 2,
    marginBottom: 12,
  },
  mediaFrameDoc: {
    width: '100%',
    aspectRatio: 85.6 / 53.98, // ID-1 / credit-card aspect ratio
    backgroundColor: '#fff',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  mediaFramePortrait: {
    width: 200,
    maxWidth: '100%',
    aspectRatio: 3 / 4,
    backgroundColor: '#fff',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'center',
    overflow: 'hidden',
  },
  resultImage: {
    width: '100%',
    height: '100%',
  },
  fieldsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  badge: {
    backgroundColor: '#ff8f0d',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 3,
    marginLeft: 8,
  },
  badgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700',
  },
  fieldRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 7,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e3e5e8',
  },
  fieldLabel: {
    color: '#888',
    fontSize: 13,
  },
  fieldValue: {
    color: '#111',
    fontSize: 13,
    fontWeight: '600',
    flexShrink: 1,
    marginLeft: 12,
    textAlign: 'right',
  },
});
