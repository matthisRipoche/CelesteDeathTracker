import { Component, computed, input } from '@angular/core';
import { RoomOutcome, RoomOutcomeKind } from '../../core/face.model';

const LABEL: Record<RoomOutcomeKind, string> = {
  cleared: 'franchie',
  died: 'morte ici',
  left: 'terminée ici',
};

/**
 * Chaque tentative entrée dans la room, de la plus ancienne à la plus récente : un carré plein quand elle
 * franchit la room, rouge quand elle y meurt, creux quand elle s'y termine sans mourir (abandon).
 * La couleur ne porte pas seule le sens : la forme change aussi, et chaque carré a un texte alternatif.
 */
@Component({
  selector: 'app-room-outcomes',
  template: `
    <ol class="strip" [attr.aria-label]="summary()">
      @for (o of outcomes(); track o.attemptId) {
        <li class="cell" [class]="o.outcome" [title]="titleOf(o)">
          <span class="ds-sr-only">{{ labelOf(o.outcome) }}</span>
        </li>
      }
    </ol>
    <p class="legend">
      <span class="key"><i class="cell cleared"></i> Franchie</span>
      <span class="key"><i class="cell died"></i> Morte ici</span>
      <span class="key"><i class="cell left"></i> Terminée ici sans mourir</span>
    </p>
  `,
  styles: `
    .strip { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 4px; }
    .cell { display: block; width: 18px; height: 18px; box-sizing: border-box; border: 2px solid transparent; }
    i.cell { display: inline-block; width: 14px; height: 14px; vertical-align: middle; }
    .cleared { background: var(--color-text); border-color: var(--color-text); }
    .died { background: var(--color-danger); border-color: var(--color-danger); border-radius: 50%; }
    .left { border-color: var(--color-text-muted); background: transparent; }
    .legend { display: flex; gap: var(--space-5); flex-wrap: wrap; margin: var(--space-3) 0 0; font-size: var(--text-sm); color: var(--color-text-muted); }
    .key { display: inline-flex; align-items: center; gap: var(--space-2); }
  `,
})
export class RoomOutcomes {
  readonly outcomes = input.required<RoomOutcome[]>();

  protected readonly summary = computed(() => {
    const count = (k: RoomOutcomeKind) => this.outcomes().filter((o) => o.outcome === k).length;
    return `${this.outcomes().length} tentatives : ${count('cleared')} franchies, ${count('died')} mortes ici, ${count('left')} terminées ici`;
  });

  protected labelOf(kind: RoomOutcomeKind): string {
    return LABEL[kind];
  }

  protected titleOf(o: RoomOutcome): string {
    return `Tentative ${LABEL[o.outcome]}, ${new Date(o.startedAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}`;
  }
}
