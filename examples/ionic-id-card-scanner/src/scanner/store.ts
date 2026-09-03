import { reactive } from 'vue';
import type { IdScanResult } from './IdScannerNative';

/**
 * Very small in-memory store shared between the home page and the result page.
 * The scan result is produced by a native activity / view controller, so it is
 * handed back to the web layer as plain JSON before navigation.
 */
export const scanStore = reactive<{ result: IdScanResult | null }>({
  result: null,
});
