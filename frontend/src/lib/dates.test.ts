import { addDays, ageOn, mondayOf, todayIn, weekStarts } from './dates';

describe('dates', () => {
  it('finds the Monday of a week', () => {
    expect(mondayOf('2026-11-20')).toBe('2026-11-16'); // Friday
    expect(mondayOf('2026-11-16')).toBe('2026-11-16'); // Monday
    expect(mondayOf('2026-11-22')).toBe('2026-11-16'); // Sunday
  });

  it('adds days across month and year ends', () => {
    expect(addDays('2026-12-28', 7)).toBe('2027-01-04');
  });

  it('uses the account time zone for today', () => {
    // Sunday 22:30 UTC is already Monday in Tallinn (UTC+2)
    const now = new Date('2026-11-22T22:30:00Z');
    expect(todayIn('Europe/Tallinn', now)).toBe('2026-11-23');
    expect(todayIn('UTC', now)).toBe('2026-11-22');
    expect(weekStarts('Europe/Tallinn', now)).toEqual({ thisMonday: '2026-11-23', nextMonday: '2026-11-30' });
  });

  it('computes age in full years', () => {
    expect(ageOn('2018-11-21', '2026-11-20')).toBe(7);
    expect(ageOn('2018-11-20', '2026-11-20')).toBe(8);
  });
});
