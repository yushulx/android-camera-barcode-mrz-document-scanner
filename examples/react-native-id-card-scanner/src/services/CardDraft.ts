import {EMPTY_CARD_INFO, type CardInfo, type CardSide} from '../models/ScannedIDCard';

export interface CardDraftState {
  /** Storage key of the card being edited, empty for a new card. */
  cardKey: string;
  frontImage: string;
  backImage: string;
  info: CardInfo;
}

const empty = (): CardDraftState => ({
  cardKey: '',
  frontImage: '',
  backImage: '',
  info: {...EMPTY_CARD_INFO},
});

let draft = empty();

/**
 * The card being edited, kept outside the component tree.
 *
 * The camera screen writes a capture here and pops back, and the card screen
 * reads it when it regains focus. Passing the image through navigation params
 * does not work: replacing the params rebuilds the card screen, and a rebuilt
 * screen starts from empty state — which loses the image of the other side.
 */
export const CardDraft = {
  read(): CardDraftState {
    return draft;
  },
  patch(patch: Partial<CardDraftState>) {
    draft = {...draft, ...patch};
  },
  /** Stores a capture coming back from the camera screen. */
  applyCapture(side: CardSide, base64: string, info?: CardInfo) {
    draft =
      side === 'front'
        ? {...draft, frontImage: base64, info: info ?? draft.info}
        : {...draft, backImage: base64, info: info ?? draft.info};
  },
  reset() {
    draft = empty();
  },
};
