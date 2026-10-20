import { formatClock, formatDuration, modOf, percent, shortMapName } from './format';

describe('format', () => {
  it('formate une durée en m:ss', () => {
    expect(formatDuration(83_000)).toBe('1:23');
    expect(formatDuration(5_400)).toBe('0:05');
    expect(formatDuration(252_000)).toBe('4:12');
  });

  it('formate un chronomètre, avec les heures au-delà de 60 minutes', () => {
    expect(formatClock(102_000)).toBe('1:42');
    expect(formatClock(3_909_000)).toBe('1:05:09');
    expect(formatClock(-5)).toBe('0:00');
    expect(formatClock(null)).toBe('0:00');
  });

  it("affiche un tiret quand la durée est inconnue", () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(undefined)).toBe('—');
  });

  it('extrait le nom court et le mod d\'un SID', () => {
    expect(shortMapName('Celeste/1-ForsakenCity')).toBe('1-ForsakenCity');
    expect(shortMapName('monikadsidespack/0/7-Summit')).toBe('7-Summit');
    expect(modOf('monikadsidespack/0/7-Summit')).toBe('monikadsidespack');
  });

  it('arrondit un pourcentage', () => {
    expect(percent(0.25)).toBe('25 %');
    expect(percent(0.0417)).toBe('4 %');
  });
});
