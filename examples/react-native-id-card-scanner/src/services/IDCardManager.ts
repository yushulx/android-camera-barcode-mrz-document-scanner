import AsyncStorage from '@react-native-async-storage/async-storage';
import type {ScannedIDCard} from '../models/ScannedIDCard';

/**
 * Storage keys are namespaced so that `getAllKeys()` can tell our records apart
 * from anything else the app (or a library) puts into AsyncStorage.
 */
const KEY_PREFIX = 'idcard:';

export class IDCardManager {
  static async save(card: ScannedIDCard): Promise<void> {
    await AsyncStorage.setItem(KEY_PREFIX + card.timestamp, JSON.stringify(card));
  }

  static async remove(timestamp: number): Promise<void> {
    await AsyncStorage.removeItem(KEY_PREFIX + timestamp);
  }

  /** Timestamps of the stored cards, newest first. */
  static async listKeys(): Promise<string[]> {
    const keys = await AsyncStorage.getAllKeys();
    return keys
      .filter(key => key.startsWith(KEY_PREFIX))
      .map(key => key.slice(KEY_PREFIX.length))
      .sort((a, b) => Number(b) - Number(a));
  }

  static async load(key: string): Promise<ScannedIDCard | null> {
    const json = await AsyncStorage.getItem(KEY_PREFIX + key);
    return json ? (JSON.parse(json) as ScannedIDCard) : null;
  }
}
