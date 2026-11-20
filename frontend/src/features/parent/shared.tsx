import { useId } from 'react';
import type { MonthResult, Outcome, WeekStatus, WordSource } from '../../api/types';
import { Badge } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';

const STATUS_TONE: Record<WeekStatus, string> = {
  DRAFT: 'neutral',
  SCHEDULED: 'info',
  ACTIVE: 'accent',
  COMPLETED: 'ok',
  EXPIRED: 'warn',
};

export function WeekStatusBadge({ status }: { status: WeekStatus }) {
  const { t } = useI18n();
  return <Badge tone={STATUS_TONE[status]}>{t(`week.status.${status}`)}</Badge>;
}

const RESULT_TONE: Record<MonthResult, string> = { REWARD: 'ok', GOOD: 'info', REPEAT: 'warn' };

export function MonthResultBadge({ result }: { result: MonthResult }) {
  const { t } = useI18n();
  return <Badge tone={RESULT_TONE[result]}>{t(`month.result.${result}`)}</Badge>;
}

const OUTCOME_TONE: Record<Outcome, string> = { LEARNED: 'ok', HARD: 'warn', UNLEARNED: 'error' };

export function OutcomeBadge({ outcome }: { outcome: Outcome | null }) {
  const { t } = useI18n();
  return outcome ? <Badge tone={OUTCOME_TONE[outcome]}>{t(`week.outcome.${outcome}`)}</Badge> : null;
}

export function SourceBadge({ source }: { source: WordSource }) {
  const { t } = useI18n();
  return <Badge tone={source === 'CARRY_OVER' ? 'warn' : 'neutral'}>{t(`week.source.${source}`)}</Badge>;
}

export function PointsBar({ points, max }: { points: number | null; max: number | null }) {
  const { t, formatNumber } = useI18n();
  const labelId = useId();
  const value = points ?? 0;
  const percent = max ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div className="points">
      <div className="points__label" id={labelId}>
        {t('common.points', { points: formatNumber(value), max: formatNumber(max) })}
      </div>
      <div
        className="points__bar"
        role="progressbar"
        aria-labelledby={labelId}
        aria-valuemin={0}
        aria-valuemax={max ?? 0}
        aria-valuenow={value}
      >
        <div className="points__fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
