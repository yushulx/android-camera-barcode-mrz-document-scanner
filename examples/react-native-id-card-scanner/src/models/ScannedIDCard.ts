/**
 * The identity fields the app extracts from the MRZ. The keys are also used as
 * the labels in the editor, so they are spelled the way they appear in the UI.
 */
export interface CardInfo {
  Surname: string;
  GivenName: string;
  IDNumber: string;
  DateOfBirth: string;
  DateOfExpiry: string;
}

/** Field order for the form on the card screen. */
export const CARD_INFO_FIELDS: (keyof CardInfo)[] = [
  'Surname',
  'GivenName',
  'IDNumber',
  'DateOfBirth',
  'DateOfExpiry',
];

export const EMPTY_CARD_INFO: CardInfo = {
  Surname: '',
  GivenName: '',
  IDNumber: '',
  DateOfBirth: '',
  DateOfExpiry: '',
};

/** Which side of the card a capture belongs to. */
export type CardSide = 'front' | 'back';

/** One scanned ID card, as persisted by `IDCardManager`. */
export interface ScannedIDCard {
  /** Milliseconds since epoch. Also used as the storage key. */
  timestamp: number;
  /** Base64 JPEG data without the `data:` URI prefix. Empty when not captured. */
  frontImage: string;
  backImage: string;
  info: CardInfo;
}
