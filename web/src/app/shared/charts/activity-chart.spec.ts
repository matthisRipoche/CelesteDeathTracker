import { TestBed } from '@angular/core/testing';
import { ActivityDay } from '../../core/summary.model';
import { ActivityChart } from './activity-chart';

const days = (counts: [number, number][]): ActivityDay[] =>
  counts.map(([attempts, clears], i) => ({ day: `2026-10-${String(9 + i).padStart(2, '0')}`, attempts, clears }));

describe('ActivityChart', () => {
  function render(data: ActivityDay[]): HTMLElement {
    const fixture = TestBed.createComponent(ActivityChart);
    fixture.componentRef.setInput('days', data);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('dessine une barre par jour, proportionnelle au jour le plus chargé', () => {
    const el = render(days([[0, 0], [5, 0], [10, 0]]));
    const bars = el.querySelectorAll<HTMLElement>('.bar');
    expect(bars.length).toBe(3);
    expect(bars[0].style.height).toBe('0%');
    expect(bars[1].style.height).toBe('50%');
    expect(bars[2].style.height).toBe('100%');
  });

  it('décrit chaque jour pour les lecteurs d\'écran, le dernier étant aujourd\'hui', () => {
    const el = render(days([[1, 0], [4, 1]]));
    const items = el.querySelectorAll('li');
    expect(items[0].getAttribute('aria-label')).toBe('09/10 : 1 tentative');
    expect(items[1].getAttribute('aria-label')).toBe("Aujourd'hui : 4 tentatives, dont 1 deathless réussi");
    expect(el.querySelectorAll<HTMLElement>('.win')[1].style.height).toBe('25%');
  });
});
