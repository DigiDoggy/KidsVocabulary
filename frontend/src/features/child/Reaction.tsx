/** Funny feedback that pops out of the word: a thumbs-up or a wink for a right answer, a wagging finger for a wrong one. */
export type ReactionKind = 'thumb' | 'wink' | 'finger';

const PICTURES: Record<ReactionKind, string> = {
  thumb: '👍',
  wink: '😉',
  finger: '☝️',
};

/** Decoration only: the answer itself is announced by the praise button or the correct-answer panel. */
export function Reaction({ kind }: { kind: ReactionKind }) {
  return (
    <span className={`reaction reaction--${kind}`} aria-hidden="true" data-testid="reaction" data-kind={kind}>
      {PICTURES[kind]}
    </span>
  );
}
