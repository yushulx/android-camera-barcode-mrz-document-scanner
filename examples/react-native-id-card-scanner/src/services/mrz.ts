import type {ParsedResultItem} from 'dynamsoft-capture-vision-react-native';
import type {CardInfo} from '../models/ScannedIDCard';

/**
 * Reads one field from a Code Parser result. MRZ values are padded with `<`
 * fillers and can carry the line break of the source line, so both are removed.
 */
function readField(item: ParsedResultItem, name: string): string {
  const value = item.parsedFields?.[name]?.value ?? '';
  return value.replace(/[\r\n]/g, '').replace(/<+$/, '').trim();
}

const CURRENT_YEAR_IN_CENTURY = new Date().getFullYear() % 100;

/**
 * MRTD dates are printed as YYMMDD, without a century. A birth date cannot be
 * in the future, so a two-digit year above the current one belongs to the
 * previous century. Expiry dates sit in the near future, so they are matched
 * against a 20-year window instead.
 */
function expandYear(twoDigitYear: string, isExpiry: boolean): string {
  const value = Number(twoDigitYear);
  if (!twoDigitYear || Number.isNaN(value)) {
    return '';
  }
  if (!isExpiry) {
    return value > CURRENT_YEAR_IN_CENTURY
      ? `19${twoDigitYear}`
      : `20${twoDigitYear}`;
  }
  return value >= CURRENT_YEAR_IN_CENTURY &&
    value <= CURRENT_YEAR_IN_CENTURY + 20
    ? `20${twoDigitYear}`
    : `19${twoDigitYear}`;
}

/** Combines the `birthYear` / `birthMonth` / `birthDay` field triple into ISO form. */
function readDate(item: ParsedResultItem, prefix: 'birth' | 'expiry'): string {
  const isExpiry = prefix === 'expiry';
  const year = expandYear(readField(item, `${prefix}Year`), isExpiry);
  const month = readField(item, `${prefix}Month`).padStart(2, '0');
  const day = readField(item, `${prefix}Day`).padStart(2, '0');
  if (!year || !month || !day) {
    return '';
  }
  return `${year}-${month}-${day}`;
}

/**
 * Maps a parsed MRTD result onto the fields the app stores. Dynamsoft Code
 * Parser already resolves the ICAO field names, so no third-party MRZ parser is
 * needed: `primaryIdentifier` is the surname, `secondaryIdentifier` the given
 * names, and the document number is exposed as `documentNumber` on ID cards and
 * as `passportNumber` on passports.
 */
export function cardInfoFromMRZ(item: ParsedResultItem): CardInfo {
  const documentNumber =
    readField(item, 'documentNumber') || readField(item, 'passportNumber');

  return {
    Surname: readField(item, 'primaryIdentifier'),
    GivenName: readField(item, 'secondaryIdentifier'),
    IDNumber: documentNumber,
    DateOfBirth: readDate(item, 'birth'),
    DateOfExpiry: readDate(item, 'expiry'),
  };
}
