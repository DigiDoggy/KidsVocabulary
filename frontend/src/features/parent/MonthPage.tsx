import { Link, useParams } from 'react-router-dom';
import { getMonth, getMonthReport } from '../../api/client';
import type { MonthReport, ReportWord } from '../../api/types';
import { Card, ErrorMessage, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { MonthResultBadge, PointsBar, WeekStatusBadge } from './shared';

export function MonthPage() {
  const monthId = Number(useParams().monthId);
  const { t, formatDate, formatNumber } = useI18n();
  const loaded = useLoad(async () => {
    const month = await getMonth(monthId);
    const report = month.status === 'COMPLETED' ? await getMonthReport(monthId) : null;
    return { month, report };
  }, [monthId]);
  usePageTitle(loaded.data && t('child.monthTitle', { n: loaded.data.month.seqNo }));

  if (loaded.error) return <ErrorMessage error={loaded.error} onRetry={loaded.reload} />;
  if (!loaded.data) return <Spinner />;
  const { month, report } = loaded.data;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="crumbs">
            <Link to="/">{t('nav.children')}</Link>
          </p>
          <h1>
            {t('child.monthTitle', { n: month.seqNo })}{' '}
            {month.result ? <MonthResultBadge result={month.result} /> : null}
          </h1>
        </div>
      </div>
      {report ? (
        <ReportView report={report} />
      ) : (
        <div className="stack">
          <Card title={t('month.progress')}>
            <p className="muted">{t('month.weeksDone', { n: month.weeks.filter((w) => w.points !== null).length })}</p>
            <PointsBar points={month.points} max={month.maxPoints} />
            <p>
              {t('child.reward')}: {month.reward ? <strong>{month.reward}</strong> : <span className="muted">{t('month.noReward')}</span>}
            </p>
          </Card>
          <Card title={t('child.weeks')}>
            {month.weeks.length === 0 && <p className="muted">{t('month.noWeeks')}</p>}
            <ul className="rows">
              {month.weeks.map((w) => (
                <li key={w.id}>
                  <Link to={`/weeks/${w.id}`}>
                    {w.weekIndex && `${t('week.indexShort', { n: w.weekIndex })} · `}
                    {formatDate(w.startDate)}
                  </Link>
                  <span>
                    {w.points !== null && `${formatNumber(w.points)} / ${formatNumber(w.maxPoints)} `}
                    <WeekStatusBadge status={w.status} />
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      )}
    </>
  );
}

function ReportView({ report }: { report: MonthReport }) {
  const { t, formatDate, formatNumber } = useI18n();
  return (
    <div className="stack">
      <Card title={t('month.report')}>
        <div className="report-total">
          <span className="report-total__percent">{formatNumber(Math.round(report.percent * 10) / 10)} %</span>
          <MonthResultBadge result={report.result} />
        </div>
        <PointsBar points={report.totalPoints} max={report.maxPoints} />
        <p className="report-verdict">{t(`month.verdict.${report.result}`)}</p>
        {report.result === 'REWARD' && (
          <p>
            {t('child.reward')}:{' '}
            {report.reward ? <strong>{report.reward}</strong> : <span className="warn-text">{t('month.noRewardSet')}</span>}
          </p>
        )}
      </Card>
      <Card title={t('child.weeks')}>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t('month.col.week')}</th>
                <th className="num">{t('month.col.points')}</th>
                <th className="num">%</th>
                <th className="num">{t('week.outcome.LEARNED')}</th>
                <th className="num">{t('week.outcome.HARD')}</th>
                <th className="num">{t('week.outcome.UNLEARNED')}</th>
              </tr>
            </thead>
            <tbody>
              {report.weeks.map((w) => (
                <tr key={w.index}>
                  <td>
                    {t('week.indexShort', { n: w.index })} · {formatDate(w.startDate)}
                  </td>
                  <td className="num">
                    {formatNumber(w.points)} / {formatNumber(w.maxPoints)}
                  </td>
                  <td className="num">{formatNumber(Math.round(w.percent))}</td>
                  <td className="num">{w.learned}</td>
                  <td className="num">{w.hard}</td>
                  <td className="num">{w.unlearned}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <div className="grid">
        <WordList title={t('month.hardWords')} hint={t('month.hardHint')} words={report.hardWords} />
        <WordList title={t('month.unlearnedWords')} hint={t('month.unlearnedHint')} words={report.unlearnedWords} />
      </div>
    </div>
  );
}

function WordList({ title, hint, words }: { title: string; hint: string; words: ReportWord[] }) {
  const { t } = useI18n();
  return (
    <Card title={`${title} (${words.length})`}>
      <p className="field__hint">{hint}</p>
      {words.length === 0 ? (
        <p className="muted">{t('month.none')}</p>
      ) : (
        <ul className="rows">
          {words.map((w) => (
            <li key={w.conceptId}>
              <span>{w.prompt}</span>
              <strong>{w.answer}</strong>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
