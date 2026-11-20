/** Calendar helpers on ISO `YYYY-MM-DD` strings (no time zone arithmetic on Date objects). */

/** Today's date in the given IANA time zone. */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  // en-CA formats as YYYY-MM-DD
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

/** Monday of the week (Mon–Sun) that contains the date. */
export function mondayOf(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = Sunday
  return addDays(isoDate, -((weekday + 6) % 7));
}

export interface WeekStarts {
  thisMonday: string;
  nextMonday: string;
}

export function weekStarts(timeZone: string, now: Date = new Date()): WeekStarts {
  const thisMonday = mondayOf(todayIn(timeZone, now));
  return { thisMonday, nextMonday: addDays(thisMonday, 7) };
}

export function ageOn(birthDate: string, isoToday: string): number {
  const [by, bm, bd] = birthDate.split('-').map(Number);
  const [ty, tm, td] = isoToday.split('-').map(Number);
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0);
}
