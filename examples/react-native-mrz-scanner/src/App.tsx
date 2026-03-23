import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  BackHandler,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  EnumResultStatus,
  MRZScanConfig,
  MRZScanner,
} from 'dynamsoft-mrz-scanner-bundle-react-native';

// ─── Design tokens ────────────────────────────────────────────────────────────
const COLORS = {
  primary: '#1A73E8',
  primaryDark: '#1558B0',
  background: '#F5F7FA',
  surface: '#FFFFFF',
  border: '#E0E6F0',
  textPrimary: '#1C2B3A',
  textSecondary: '#5A6A7A',
  textMuted: '#9AA5B1',
  success: '#1E8A4C',
  error: '#D93025',
  errorBg: '#FEF0EF',
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Convert camelCase / UPPER_CASE field names to "Title Case" labels */
function formatLabel(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/_/g, ' ')
    .replace(/^\s/, '')
    .replace(/\b\w/g, c => c.toUpperCase());
}

/** Fields to show at the top as "hero" info, in order */
const HERO_FIELDS = ['firstName', 'lastName', 'documentNumber', 'documentType'];

// ─── Sub-components ───────────────────────────────────────────────────────────

function Header() {
  return (
    <View style={styles.header}>
      <Text style={styles.headerTitle}>MRZ Scanner</Text>
      <Text style={styles.headerSubtitle}>Scan passport or travel document</Text>
    </View>
  );
}

function EmptyState() {
  return (
    <View style={styles.emptyState}>
      <View style={styles.emptyIconContainer}>
        <Text style={styles.emptyIcon}>🛂</Text>
      </View>
      <Text style={styles.emptyTitle}>No document scanned yet</Text>
      <Text style={styles.emptyBody}>
        Tap the button below to scan a passport, ID card, or other travel
        document with a Machine Readable Zone.
      </Text>
    </View>
  );
}

interface ResultCardProps {
  data: Record<string, string>;
}

function ResultCard({data}: ResultCardProps) {
  const heroEntries = HERO_FIELDS.map(k => [k, data[k]] as [string, string]).filter(
    ([, v]) => v != null && v !== '',
  );
  const otherEntries = Object.entries(data).filter(
    ([k, v]) => !HERO_FIELDS.includes(k) && v != null && v !== '',
  );

  return (
    <View style={styles.resultCard}>
      {/* Hero section */}
      {heroEntries.length > 0 && (
        <View style={styles.heroSection}>
          {heroEntries.map(([key, value]) => (
            <View key={key} style={styles.heroRow}>
              <Text style={styles.heroLabel}>{formatLabel(key)}</Text>
              <Text style={styles.heroValue}>{value}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Divider */}
      {otherEntries.length > 0 && <View style={styles.divider} />}

      {/* Other fields */}
      {otherEntries.map(([key, value], index) => (
        <View
          key={key}
          style={[
            styles.fieldRow,
            index < otherEntries.length - 1 && styles.fieldRowBorder,
          ]}>
          <Text style={styles.fieldLabel}>{formatLabel(key)}</Text>
          <Text style={styles.fieldValue}>{value}</Text>
        </View>
      ))}
    </View>
  );
}

interface ErrorBannerProps {
  message: string;
}

function ErrorBanner({message}: ErrorBannerProps) {
  return (
    <View style={styles.errorBanner}>
      <Text style={styles.errorIcon}>⚠️</Text>
      <Text style={styles.errorMessage}>{message}</Text>
    </View>
  );
}

interface CancelledBannerProps {}

function CancelledBanner(_props: CancelledBannerProps) {
  return (
    <View style={styles.cancelledBanner}>
      <Text style={styles.cancelledText}>Scan was cancelled.</Text>
    </View>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

type ScanState =
  | {kind: 'idle'}
  | {kind: 'scanning'}
  | {kind: 'result'; data: Record<string, string>}
  | {kind: 'cancelled'}
  | {kind: 'error'; code: number; message: string};

function App(): React.JSX.Element {
  const [scanState, setScanState] = useState<ScanState>({kind: 'idle'});

  // Intercept Android hardware back button: go to idle instead of closing the app.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (scanState.kind !== 'idle') {
        setScanState({kind: 'idle'});
        return true; // event handled — prevent default (app exit)
      }
      return false; // let the system handle it (idle → exit as normal)
    });
    return () => subscription.remove();
  }, [scanState.kind]);

  const handleScan = async () => {
    setScanState({kind: 'scanning'});
    try {
      const config: MRZScanConfig = {
        // Trial license — network connection required.
        // Request an extension at:
        // https://www.dynamsoft.com/customer/license/trialLicense/?product=dcv&package=cross-platform
        license:
          'DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==',
      };
      const result = await MRZScanner.launch(config);

      if (result.resultStatus === EnumResultStatus.RS_FINISHED) {
        setScanState({kind: 'result', data: result.data as Record<string, string>});
      } else if (result.resultStatus === EnumResultStatus.RS_CANCELED) {
        setScanState({kind: 'cancelled'});
      } else {
        setScanState({
          kind: 'error',
          code: result.errorCode ?? -1,
          message: result.errorString ?? 'An unknown error occurred.',
        });
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      setScanState({kind: 'error', code: -1, message});
    }
  };

  const isScanning = scanState.kind === 'scanning';

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar
        barStyle="dark-content"
        backgroundColor={COLORS.surface}
        translucent={false}
      />

      <Header />

      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}>
        {scanState.kind === 'idle' && <EmptyState />}
        {scanState.kind === 'scanning' && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={COLORS.primary} />
            <Text style={styles.loadingText}>Opening scanner…</Text>
          </View>
        )}
        {scanState.kind === 'result' && <ResultCard data={scanState.data} />}
        {scanState.kind === 'cancelled' && <CancelledBanner />}
        {scanState.kind === 'error' && (
          <ErrorBanner
            message={`Error ${scanState.code}: ${scanState.message}`}
          />
        )}
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.scanButton, isScanning && styles.scanButtonDisabled]}
          onPress={handleScan}
          disabled={isScanning}
          activeOpacity={0.8}>
          {isScanning ? (
            <ActivityIndicator color="#FFFFFF" size="small" />
          ) : (
            <Text style={styles.scanButtonText}>
              {scanState.kind === 'result' || scanState.kind === 'error' || scanState.kind === 'cancelled'
                ? '🔄  Scan Again'
                : '📷  Scan MRZ'}
            </Text>
          )}
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: COLORS.surface,
  },

  // Header
  header: {
    backgroundColor: COLORS.surface,
    paddingHorizontal: 20,
    paddingTop: Platform.OS === 'android' ? 16 : 12,
    paddingBottom: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: COLORS.border,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: COLORS.textPrimary,
    letterSpacing: 0.3,
  },
  headerSubtitle: {
    fontSize: 13,
    color: COLORS.textMuted,
    marginTop: 2,
  },

  // Scroll
  scrollView: {flex: 1, backgroundColor: COLORS.background},
  scrollContent: {flexGrow: 1, padding: 20},

  // Empty state
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
    paddingHorizontal: 24,
  },
  emptyIconContainer: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: COLORS.border,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
  },
  emptyIcon: {fontSize: 36},
  emptyTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: COLORS.textPrimary,
    marginBottom: 10,
    textAlign: 'center',
  },
  emptyBody: {
    fontSize: 14,
    color: COLORS.textSecondary,
    textAlign: 'center',
    lineHeight: 21,
  },

  // Loading
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 80,
  },
  loadingText: {
    marginTop: 14,
    fontSize: 15,
    color: COLORS.textSecondary,
  },

  // Result card
  resultCard: {
    backgroundColor: COLORS.surface,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: COLORS.border,
    overflow: 'hidden',
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: {width: 0, height: 2},
        shadowOpacity: 0.06,
        shadowRadius: 8,
      },
      android: {elevation: 3},
    }),
  },
  heroSection: {
    padding: 16,
    backgroundColor: '#EAF2FF',
  },
  heroRow: {
    marginBottom: 8,
  },
  heroLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: COLORS.primary,
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginBottom: 2,
  },
  heroValue: {
    fontSize: 16,
    fontWeight: '700',
    color: COLORS.textPrimary,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: COLORS.border,
  },
  fieldRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  fieldRowBorder: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: COLORS.border,
  },
  fieldLabel: {
    fontSize: 13,
    color: COLORS.textSecondary,
    flex: 1,
  },
  fieldValue: {
    fontSize: 13,
    fontWeight: '600',
    color: COLORS.textPrimary,
    flex: 2,
    textAlign: 'right',
  },

  // Cancelled banner
  cancelledBanner: {
    padding: 16,
    backgroundColor: COLORS.surface,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: COLORS.border,
    alignItems: 'center',
    marginTop: 24,
  },
  cancelledText: {
    fontSize: 15,
    color: COLORS.textSecondary,
  },

  // Error banner
  errorBanner: {
    padding: 16,
    backgroundColor: COLORS.errorBg,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#F5C6C3',
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    marginTop: 24,
  },
  errorIcon: {fontSize: 18, lineHeight: 22},
  errorMessage: {
    flex: 1,
    fontSize: 13,
    color: COLORS.error,
    lineHeight: 20,
  },

  // Footer / scan button
  footer: {
    padding: 16,
    paddingBottom: Platform.OS === 'android' ? 20 : 16,
    backgroundColor: COLORS.surface,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: COLORS.border,
  },
  scanButton: {
    backgroundColor: COLORS.primary,
    borderRadius: 12,
    paddingVertical: 15,
    alignItems: 'center',
    justifyContent: 'center',
    ...Platform.select({
      ios: {
        shadowColor: COLORS.primary,
        shadowOffset: {width: 0, height: 4},
        shadowOpacity: 0.35,
        shadowRadius: 8,
      },
      android: {elevation: 4},
    }),
  },
  scanButtonDisabled: {
    backgroundColor: COLORS.textMuted,
    ...Platform.select({
      ios: {shadowOpacity: 0},
      android: {elevation: 0},
    }),
  },
  scanButtonText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FFFFFF',
    letterSpacing: 0.4,
  },
});

export default App;
