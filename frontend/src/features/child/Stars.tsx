import { useI18n } from '../../i18n/I18nProvider';

/** Points as a star counter with a progress bar: the child sees "how far", not a percentage. */
export function Stars({ points, max }: { points: number | null; max: number | null }) {
  const { t, formatNumber } = useI18n();
  const value = points ?? 0;
  const percent = max ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div className="stars">
      <div className="stars__count">
        <span aria-hidden="true">⭐</span> {t('kid.points', { points: formatNumber(value), max: formatNumber(max) })}
      </div>
      <div className="stars__bar" aria-hidden="true">
        <div className="stars__fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
