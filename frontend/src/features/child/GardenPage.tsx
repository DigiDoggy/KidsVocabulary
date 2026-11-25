import { useState } from 'react';
import { Link } from 'react-router-dom';
import { myGarden } from '../../api/client';
import type { Garden, GardenPlant } from '../../api/types';
import { ErrorMessage, Modal, Spinner } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { usePageTitle } from '../../lib/a11y';
import { useLoad } from '../../lib/useLoad';
import { useChildContext } from './ChildLayout';
import { PlantPicture } from './PlantPicture';

/** The word garden: one plant per learned word, in beds by study month. */
export function GardenPage() {
  const { t } = useI18n();
  usePageTitle(t('kid.garden'));
  const garden = useLoad(myGarden, []);
  const [open, setOpen] = useState<GardenPlant | null>(null);

  if (garden.error) {
    return (
      <div className="kid__page">
        <ErrorMessage error={garden.error} onRetry={garden.reload} />
      </div>
    );
  }
  if (!garden.data) return <Spinner />;
  const data = garden.data;
  return (
    <div className="kid__page garden">
      <div className="garden__head">
        <Link className="train__home" to="/child" aria-label={t('kid.home')}>
          <span aria-hidden="true">🏠</span>
        </Link>
        <h1 className="garden__title">
          <span aria-hidden="true">🌱</span> {t('kid.garden')}
        </h1>
      </div>
      <WaterCard garden={data} />
      {data.beds.map((bed) => (
        <section key={bed.month ?? 'earlier'} className="garden__bed" aria-label={bedName(bed.month, t)}>
          <h2 className="garden__bed-title">{bedName(bed.month, t)}</h2>
          <ul className="garden__plants">
            {bed.plants.map((p) => (
              <li key={p.conceptId}>
                <button
                  type="button"
                  className={`garden__plant${p.due ? ' is-due' : ''}`}
                  aria-label={t(p.thirsty ? 'kid.plantThirstyLabel' : 'kid.plantLabel', {
                    word: p.answer,
                    stage: t(`kid.stage${p.stage}`),
                  })}
                  onClick={() => setOpen(p)}
                >
                  <PlantPicture kind={p.kind} stage={p.stage} thirsty={p.thirsty} />
                  {p.due && (
                    <span className="garden__drop" aria-hidden="true">
                      💧
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
      {open && <PlantDialog plant={open} onClose={() => setOpen(null)} />}
    </div>
  );
}

function bedName(month: number | null, t: ReturnType<typeof useI18n>['t']) {
  return month === null ? t('kid.bedEarlier') : t('kid.bed', { n: month });
}

function WaterCard({ garden }: { garden: Garden }) {
  const { t } = useI18n();
  if (garden.plantCount === 0) {
    return <p className="kid__card kid__text">{t('kid.gardenEmpty')}</p>;
  }
  const canWater = garden.openReviewId !== null || garden.dueCount > 0;
  return (
    <section className="kid__card garden__water">
      <p className="kid__text">{t('kid.gardenCount', { count: garden.plantCount })}</p>
      {canWater ? (
        <>
          {garden.dueCount > 0 && <p className="kid__text">{t('kid.dueCount', { count: garden.dueCount })}</p>}
          <Link className="btn btn--accent btn--huge" to="/child/garden/water">
            <span aria-hidden="true">💧</span>{' '}
            {garden.openReviewId !== null ? t('kid.waterContinue') : t('kid.water')}
          </Link>
          <p className="muted">{t('kid.waterNote')}</p>
        </>
      ) : (
        <p className="kid__text">{t('kid.allWatered')}</p>
      )}
    </section>
  );
}

function PlantDialog({ plant, onClose }: { plant: GardenPlant; onClose: () => void }) {
  const { t } = useI18n();
  const { home } = useChildContext();
  return (
    <Modal title={plant.answer} onClose={onClose}>
      <div className="garden__detail">
        <PlantPicture kind={plant.kind} stage={plant.stage} thirsty={plant.thirsty} className="garden__big" />
        {plant.emoji && (
          <p className="garden__emoji" aria-hidden="true">
            {plant.emoji}
          </p>
        )}
        <p className="garden__word" lang={home.child.targetLang}>
          {plant.answer}
        </p>
        <p className="garden__prompt" lang={home.child.nativeLang}>
          {plant.prompt}
        </p>
        <p className="garden__stage">{t(`kid.stage${plant.stage}`)}</p>
        {plant.thirsty && <p className="garden__thirsty">{t('kid.thirsty')}</p>}
        <button type="button" className="btn btn--accent" onClick={onClose}>
          {t('kid.ok')}
        </button>
      </div>
    </Modal>
  );
}
