import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { archiveChild, createChild, getChild, updateChild } from '../../api/client';
import { ApiError } from '../../api/http';
import type { Child, ChildData, Level } from '../../api/types';
import { ErrorMessage, Field, Spinner, useAction } from '../../components/ui';
import { useI18n } from '../../i18n/I18nProvider';
import { useLoad } from '../../lib/useLoad';

const LEVELS: Level[] = ['A0', 'A1', 'A2'];
const NATIVE_LANGS = ['ru', 'et'];
const TARGET_LANGS = ['en'];
const DEFAULT_WEEK_SIZE = 10;

export function ChildFormPage() {
  const { id } = useParams();
  const childId = id ? Number(id) : null;
  const child = useLoad(() => (childId ? getChild(childId) : Promise.resolve(null)), [childId]);
  const { t } = useI18n();

  if (child.error) return <ErrorMessage error={child.error} />;
  if (child.loading) return <Spinner />;
  return (
    <>
      <div className="page-head">
        <h1>{childId ? t('childForm.editTitle') : t('childForm.newTitle')}</h1>
      </div>
      <ChildForm initial={child.data ?? null} />
    </>
  );
}

function ChildForm({ initial }: { initial: Child | null }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [data, setData] = useState<ChildData>(
    initial ?? {
      name: '',
      birthDate: null,
      level: 'A0',
      nativeLang: 'ru',
      targetLang: 'en',
      defaultWeekSize: DEFAULT_WEEK_SIZE,
    },
  );
  const [run, busy, error] = useAction();
  const [archive, archiving, archiveError] = useAction();
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {};
  const today = new Date().toISOString().slice(0, 10);

  const set = <K extends keyof ChildData>(key: K, value: ChildData[K]) => setData((d) => ({ ...d, [key]: value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    const body = { ...data, name: data.name.trim(), birthDate: data.birthDate || null };
    const saved = await run(() => (initial ? updateChild(initial.id, body) : createChild(body)));
    if (saved) {
      navigate(`/children/${saved.id}`, { replace: true });
    }
  }

  async function onArchive() {
    if (!initial || !window.confirm(t('childForm.archiveConfirm', { name: initial.name }))) return;
    const done = await archive(() => archiveChild(initial.id).then(() => true));
    if (done) navigate('/', { replace: true });
  }

  const fieldError = (name: string) => (fieldErrors[name] ? t(`childForm.invalid.${name}`) : undefined);

  return (
    <form className="form card" onSubmit={submit}>
      <Field label={t('childForm.name')} error={fieldError('name')}>
        <input required maxLength={50} value={data.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label={t('childForm.birthDate')} error={fieldError('birthDate')}>
        <input
          type="date"
          max={today}
          value={data.birthDate ?? ''}
          onChange={(e) => set('birthDate', e.target.value || null)}
        />
      </Field>
      <fieldset className="field">
        <legend className="field__label">{t('childForm.level')}</legend>
        <div className="segmented">
          {LEVELS.map((l) => (
            <label key={l} className={`segmented__item${data.level === l ? ' is-active' : ''}`}>
              <input type="radio" name="level" value={l} checked={data.level === l} onChange={() => set('level', l)} />
              <span>
                <strong>{l}</strong> <small>{t(`levels.${l}`)}</small>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      <div className="form__row">
        <Field label={t('childForm.nativeLang')}>
          <select value={data.nativeLang} onChange={(e) => set('nativeLang', e.target.value)}>
            {NATIVE_LANGS.map((l) => (
              <option key={l} value={l}>
                {t(`languages.name.${l}`)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t('childForm.targetLang')}>
          <select value={data.targetLang} onChange={(e) => set('targetLang', e.target.value)}>
            {TARGET_LANGS.map((l) => (
              <option key={l} value={l}>
                {t(`languages.name.${l}`)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <Field label={t('childForm.weekSize')} hint={t('childForm.weekSizeHint')} error={fieldError('defaultWeekSize')}>
        <input
          type="number"
          min={1}
          max={50}
          required
          value={data.defaultWeekSize}
          onChange={(e) => set('defaultWeekSize', Number(e.target.value))}
        />
      </Field>
      {Object.keys(fieldErrors).length === 0 && <ErrorMessage error={error} />}
      <ErrorMessage error={archiveError} />
      <div className="form__actions">
        <button className="btn btn--primary" disabled={busy}>
          {t('common.save')}
        </button>
        <Link className="btn btn--ghost" to={initial ? `/children/${initial.id}` : '/'}>
          {t('common.cancel')}
        </Link>
        {initial && (
          <button type="button" className="btn btn--danger form__end" disabled={archiving} onClick={onArchive}>
            {t('childForm.archive')}
          </button>
        )}
      </div>
    </form>
  );
}
