import type { ReactNode } from 'react';
import type { GrowthStage } from '../../api/types';

/**
 * The garden's drawings: one small SVG per plant kind and growth stage, drawn from shared parts so
 * the whole set costs a few kilobytes of the bundle, whatever the number of words.
 *
 * Kinds: 0 apple, 1 cherry, 2 pear (trees), 3 sunflower, 4 tulip, 5 cactus, 6 rose, 7 daisy.
 */
export const PLANT_KINDS = 8;

const LEAF = '#4caf50';
const LEAF_DARK = '#2f7d32';
const STEM = '#3f8f3a';
const TRUNK = '#8d5a2b';
const CROWN = '#5cb85c';
const CROWN_DARK = '#3e9442';

interface KindStyle {
  tree: boolean;
  blossom: string;
  fruit: string;
}

const KINDS: KindStyle[] = [
  { tree: true, blossom: '#ffd6e0', fruit: '#e03131' },
  { tree: true, blossom: '#f783ac', fruit: '#a61e4d' },
  { tree: true, blossom: '#ffffff', fruit: '#b5c934' },
  { tree: false, blossom: '#fcc419', fruit: '#fcc419' },
  { tree: false, blossom: '#e64980', fruit: '#e64980' },
  { tree: false, blossom: '#f06595', fruit: '#e8590c' },
  { tree: false, blossom: '#c2255c', fruit: '#c2255c' },
  { tree: false, blossom: '#ffffff', fruit: '#ffffff' },
];

const STAGES: GrowthStage[] = ['SEED', 'SPROUT', 'YOUNG', 'BLOOMING', 'FRUIT'];

export function PlantPicture({
  kind,
  stage,
  thirsty = false,
  className,
}: {
  kind: number;
  stage: GrowthStage;
  thirsty?: boolean;
  className?: string;
}) {
  const k = ((kind % PLANT_KINDS) + PLANT_KINDS) % PLANT_KINDS;
  const level = Math.max(0, STAGES.indexOf(stage));
  return (
    <svg
      className={`plant${thirsty ? ' plant--thirsty' : ''}${className ? ` ${className}` : ''}`}
      viewBox="0 0 100 100"
      aria-hidden="true"
      focusable="false"
      data-kind={k}
      data-stage={stage}
    >
      <Soil dry={thirsty} />
      <g className="plant__body">{body(k, level)}</g>
    </svg>
  );
}

function Soil({ dry }: { dry: boolean }) {
  return (
    <g className="plant__soil">
      <ellipse cx="50" cy="90" rx="36" ry="9" fill={dry ? '#b08455' : '#7a4b22'} />
      <ellipse cx="50" cy="87.5" rx="30" ry="5" fill={dry ? '#c49a6c' : '#8f5a2b'} />
    </g>
  );
}

function body(kind: number, level: number): ReactNode {
  if (level === 0) return <Seed />;
  if (level === 1) return kind === 5 ? <CactusColumn top={74} width={10} /> : <Sprout />;
  if (kind === 5) return <Cactus level={level} />;
  return KINDS[kind].tree ? <Tree kind={kind} level={level} /> : <Flower kind={kind} level={level} />;
}

function Seed() {
  return (
    <g>
      <ellipse cx="50" cy="80" rx="10" ry="7" fill="#d9a25f" stroke="#7a4b22" strokeWidth="1.4" transform="rotate(-20 50 80)" />
      <path d="M44 81 Q50 75 56 77" stroke="#7a4b22" strokeWidth="1.4" fill="none" strokeLinecap="round" />
      <circle cx="46.5" cy="77" r="1.6" fill="#ffffff" opacity="0.7" />
    </g>
  );
}

function Sprout() {
  return (
    <g>
      <path d="M50 87 C50 80 50 76 50 70" stroke={STEM} strokeWidth="3" strokeLinecap="round" fill="none" />
      <Leaf x={50} y={71} angle={-35} size={8} />
      <Leaf x={50} y={71} angle={215} size={8} />
    </g>
  );
}

/** A leaf growing from (x, y) in the direction of the angle (degrees, 0 = right). */
function Leaf({ x, y, angle, size }: { x: number; y: number; angle: number; size: number }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${angle})`}>
      <path
        d={`M0 0 Q${size * 0.5} ${-size * 0.45} ${size} 0 Q${size * 0.5} ${size * 0.45} 0 0Z`}
        fill={LEAF}
        stroke={LEAF_DARK}
        strokeWidth="0.8"
      />
    </g>
  );
}

function Stem({ top }: { top: number }) {
  return <path d={`M50 87 C49 ${(87 + top) / 2} 51 ${(87 + top) / 2} 50 ${top}`} stroke={STEM} strokeWidth="3.2" strokeLinecap="round" fill="none" />;
}

function Flower({ kind, level }: { kind: number; level: number }) {
  const top = level === 2 ? 52 : level === 3 ? 40 : 34;
  return (
    <g>
      <Stem top={top} />
      <Leaf x={50} y={76} angle={-30} size={13} />
      <Leaf x={50} y={70} angle={210} size={13} />
      {level >= 3 && <Leaf x={50} y={60} angle={-40} size={11} />}
      {level === 2 && <Leaf x={50} y={56} angle={235} size={9} />}
      {level === 3 && <Bud kind={kind} y={top} />}
      {level === 4 && <Bloom kind={kind} x={50} y={top - 4} scale={1} />}
      {level === 4 && kind === 7 && (
        <>
          <path d="M50 66 Q40 58 34 54" stroke={STEM} strokeWidth="2.2" fill="none" />
          <path d="M50 62 Q60 56 66 50" stroke={STEM} strokeWidth="2.2" fill="none" />
          <Bloom kind={kind} x={33} y={52} scale={0.6} />
          <Bloom kind={kind} x={67} y={48} scale={0.6} />
        </>
      )}
    </g>
  );
}

function Bud({ kind, y }: { kind: number; y: number }) {
  const color = kind === 3 ? '#8bc34a' : KINDS[kind].blossom;
  return (
    <g>
      <ellipse cx="50" cy={y - 5} rx="5.5" ry="8" fill={color} stroke={kind === 7 ? '#ced4da' : 'none'} />
      {kind === 3 && <ellipse cx="50" cy={y - 11} rx="3" ry="2.4" fill="#fcc419" />}
      <path d={`M44.5 ${y - 1} Q50 ${y + 3} 55.5 ${y - 1} L50 ${y - 6}Z`} fill={LEAF_DARK} />
    </g>
  );
}

function Bloom({ kind, x, y, scale }: { kind: number; x: number; y: number; scale: number }) {
  const t = `translate(${x} ${y}) scale(${scale})`;
  if (kind === 3) {
    return (
      <g transform={t}>
        {Array.from({ length: 12 }, (_, i) => (
          <ellipse key={i} cx="0" cy="-13" rx="4" ry="8" fill="#fcc419" stroke="#f59f00" strokeWidth="0.6" transform={`rotate(${i * 30})`} />
        ))}
        <circle r="8.5" fill="#7c4a03" />
        <circle cx="-3" cy="-2" r="1.1" fill="#5c3402" />
        <circle cx="2.5" cy="-3" r="1.1" fill="#5c3402" />
        <circle cx="0" cy="2.5" r="1.1" fill="#5c3402" />
        <circle cx="4" cy="2" r="1.1" fill="#5c3402" />
        <circle cx="-4" cy="3" r="1.1" fill="#5c3402" />
      </g>
    );
  }
  if (kind === 4) {
    return (
      <g transform={t}>
        <path d="M-11 -2 Q-12 -16 -6 -13 L0 -19 L6 -13 Q12 -16 11 -2 Q11 10 0 10 Q-11 10 -11 -2Z" fill="#e64980" stroke="#a61e4d" strokeWidth="0.8" />
        <path d="M0 -19 L0 8" stroke="#c2255c" strokeWidth="0.8" />
      </g>
    );
  }
  if (kind === 6) {
    return (
      <g transform={t}>
        <circle r="11" fill="#c2255c" />
        <path d="M-6 -1 Q-6 -8 0 -8 Q7 -8 7 -1 Q7 6 0 6 Q-4 6 -4 1 Q-4 -3 0 -3 Q3 -3 3 0" stroke="#87163d" strokeWidth="1.4" fill="none" />
        <path d="M-10 4 Q-12 10 -6 11" stroke="#87163d" strokeWidth="1.2" fill="none" />
      </g>
    );
  }
  // daisy
  return (
    <g transform={t}>
      {Array.from({ length: 10 }, (_, i) => (
        <ellipse key={i} cx="0" cy="-9" rx="3.2" ry="7" fill="#ffffff" stroke="#ced4da" strokeWidth="0.6" transform={`rotate(${i * 36})`} />
      ))}
      <circle r="5" fill="#fcc419" stroke="#f59f00" strokeWidth="0.6" />
    </g>
  );
}

function Tree({ kind, level }: { kind: number; level: number }) {
  const { blossom, fruit } = KINDS[kind];
  if (level === 2) {
    // a sapling: a thin woody twig with a few leaves, no crown yet
    return (
      <g>
        <path d="M50 87 L50 50" stroke={TRUNK} strokeWidth="3.2" strokeLinecap="round" />
        <path d="M50 70 L59 61 M50 62 L42 54" stroke={TRUNK} strokeWidth="2" strokeLinecap="round" />
        <Leaf x={59} y={61} angle={-50} size={10} />
        <Leaf x={59} y={61} angle={10} size={9} />
        <Leaf x={42} y={54} angle={200} size={10} />
        <Leaf x={42} y={54} angle={250} size={9} />
        <Leaf x={50} y={50} angle={-70} size={10} />
        <Leaf x={50} y={50} angle={250} size={10} />
      </g>
    );
  }
  const spots: [number, number][] = [
    [36, 44], [50, 26], [62, 38], [44, 36], [58, 50], [40, 54], [66, 50], [52, 44],
  ];
  return (
    <g>
      <path d="M46 87 L47.5 56 L52.5 56 L54 87Z" fill={TRUNK} />
      <path d="M50 64 L40 56 M50 60 L60 52" stroke={TRUNK} strokeWidth="2.4" strokeLinecap="round" />
      <circle cx="38" cy="46" r="15" fill={CROWN} stroke={CROWN_DARK} strokeWidth="1" />
      <circle cx="62" cy="46" r="15" fill={CROWN} stroke={CROWN_DARK} strokeWidth="1" />
      <circle cx="50" cy="32" r="17" fill={CROWN} stroke={CROWN_DARK} strokeWidth="1" />
      <circle cx="50" cy="46" r="12" fill={CROWN} />
      {level === 3 &&
        spots.map(([x, y], i) => (
          <g key={i} transform={`translate(${x} ${y})`}>
            {[0, 72, 144, 216, 288].map((a) => (
              <circle key={a} cx="0" cy="-2.2" r="1.9" fill={blossom} transform={`rotate(${a})`} />
            ))}
            <circle r="1.2" fill="#fab005" />
          </g>
        ))}
      {level === 4 && spots.slice(0, 6).map(([x, y], i) => <Fruit key={i} kind={kind} x={x} y={y + 2} color={fruit} />)}
    </g>
  );
}

function Fruit({ kind, x, y, color }: { kind: number; x: number; y: number; color: string }) {
  if (kind === 1) {
    return (
      <g>
        <path d={`M${x - 3} ${y + 1} Q${x} ${y - 7} ${x + 1} ${y - 8} Q${x + 2} ${y - 6} ${x + 4} ${y + 1}`} stroke="#5c3d1e" strokeWidth="0.9" fill="none" />
        <circle cx={x - 3} cy={y + 2.5} r="3" fill={color} />
        <circle cx={x + 4} cy={y + 2.5} r="3" fill={color} />
      </g>
    );
  }
  if (kind === 2) {
    return (
      <g>
        <path d={`M${x} ${y - 6} Q${x + 3} ${y - 6} ${x + 3} ${y - 1} Q${x + 5} ${y + 4} ${x} ${y + 5} Q${x - 5} ${y + 4} ${x - 3} ${y - 1} Q${x - 3} ${y - 6} ${x} ${y - 6}Z`} fill={color} stroke="#8a9a1e" strokeWidth="0.7" />
        <path d={`M${x} ${y - 6} l1 -2.5`} stroke="#5c3d1e" strokeWidth="1" />
      </g>
    );
  }
  return (
    <g>
      <circle cx={x} cy={y} r="4.2" fill={color} />
      <circle cx={x - 1.4} cy={y - 1.4} r="1.1" fill="#ffffff" opacity="0.6" />
      <path d={`M${x} ${y - 4} l1 -2.5`} stroke="#5c3d1e" strokeWidth="1" />
    </g>
  );
}

function CactusColumn({ top, width }: { top: number; width: number }) {
  const x = 50 - width / 2;
  return (
    <g>
      <rect x={x} y={top} width={width} height={88 - top} rx={width / 2} fill="#4c9a52" stroke="#2f6b34" strokeWidth="1" />
      <path d={`M50 ${top + 4} L50 86`} stroke="#3d8243" strokeWidth="1" />
    </g>
  );
}

function Cactus({ level }: { level: number }) {
  const top = level === 2 ? 56 : 40;
  const { blossom, fruit } = KINDS[5];
  return (
    <g>
      {level >= 3 && (
        <path d="M58 66 L66 66 Q70 66 70 62 L70 52" stroke="#4c9a52" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      )}
      {level === 4 && (
        <path d="M42 72 L34 72 Q30 72 30 68 L30 60" stroke="#4c9a52" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      )}
      <CactusColumn top={top} width={level === 2 ? 14 : 16} />
      {[0, 1, 2, 3].map((i) => (
        <g key={i} stroke="#e9f5db" strokeWidth="0.8">
          <path d={`M${44} ${top + 8 + i * 9} l-3 -1`} />
          <path d={`M${56} ${top + 12 + i * 9} l3 -1`} />
        </g>
      ))}
      {level >= 3 && (
        <g transform={`translate(50 ${top - 1})`}>
          {[0, 60, 120, 180, 240, 300].map((a) => (
            <ellipse key={a} cx="0" cy="-3.5" rx="2.4" ry="4" fill={blossom} transform={`rotate(${a})`} />
          ))}
          <circle r="2" fill="#ffe066" />
        </g>
      )}
      {level === 4 && (
        <>
          <ellipse cx="70" cy="48" rx="3.2" ry="4.2" fill={fruit} />
          <ellipse cx="30" cy="56" rx="3.2" ry="4.2" fill={fruit} />
        </>
      )}
    </g>
  );
}
