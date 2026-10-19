import { Component, computed, input } from '@angular/core';
import { AttemptResult } from '../../core/face.model';

type Tick = 'done' | 'end-fall' | 'end-quit' | 'empty' | 'win';

/**
 * Le « parcours » d'une tentative : une règle graduée, un trait par room. Franchie en trait plein,
 * arrêt en rouge (mort) ou en contour (abandon), rooms non atteintes vides. Une réussite est toute verte.
 */
@Component({
  selector: 'app-room-path',
  template: `
    <span class="path" role="img" [attr.aria-label]="label()">
      @for (t of ticks(); track $index) {
        <span class="tick" [class]="t" [style.width.px]="width()" [style.height.px]="height()"></span>
      }
    </span>
  `,
  styles: `
    .path { display: inline-flex; gap: 3px; vertical-align: middle; }
    .tick { display: block; box-sizing: border-box; border: 1px solid var(--color-border); }
    .done { background: var(--color-text); border-color: var(--color-text); }
    .win { background: var(--color-success); border-color: var(--color-success); }
    .end-fall { background: var(--color-danger); border-color: var(--color-danger); }
    .end-quit { border-color: var(--color-text-muted); }
  `,
})
export class RoomPath {
  /** Rooms franchies avant l'arrêt. */
  readonly cleared = input.required<number>();
  readonly total = input.required<number>();
  readonly result = input.required<AttemptResult>();
  readonly width = input(8);
  readonly height = input(20);

  protected readonly ticks = computed<Tick[]>(() => {
    const total = this.total();
    const cleared = Math.min(this.cleared(), total);
    const result = this.result();
    return Array.from({ length: total }, (_, i): Tick => {
      if (result === 'success') return 'win';
      if (i < cleared) return 'done';
      if (i === cleared) return result === 'death' ? 'end-fall' : 'end-quit';
      return 'empty';
    });
  });

  protected readonly label = computed(() => {
    if (this.result() === 'success') return `Les ${this.total()} rooms franchies`;
    return `${this.cleared()} rooms franchies sur ${this.total()}`;
  });
}
