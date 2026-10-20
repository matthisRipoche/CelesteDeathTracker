import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Injector, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Shell } from '../../shared/shell/shell';
import { Observable } from 'rxjs';
import { PairingRequestInfo, PairingService } from '../../core/pairing.service';

type Step = 'enter' | 'confirm' | 'done' | 'denied';

@Component({
  selector: 'app-pair',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, Shell],
  templateUrl: './pair.html',
  styleUrl: './pair.css',
})
export class Pair {
  private readonly pairing = inject(PairingService);
  private readonly injector = inject(Injector);
  private readonly stepTitle = viewChild<ElementRef<HTMLElement>>('stepTitle');

  protected readonly step = signal<Step>('enter');
  protected readonly request = signal<PairingRequestInfo | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Le code est volontairement saisi à la main, jamais pré-rempli par un lien : un lien piégé
  // pourrait sinon faire approuver le code d'un inconnu (voir docs/auth-and-tokens.md).
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected check(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.run(this.pairing.lookup(this.form.getRawValue().code.trim()), (info) => {
      this.request.set(info);
      this.goTo('confirm');
    });
  }

  protected approve(): void {
    const req = this.request();
    if (!req) return;
    this.run(this.pairing.approve(req.userCode), () => this.goTo('done'));
  }

  protected deny(): void {
    const req = this.request();
    if (!req) return;
    this.run(this.pairing.deny(req.userCode), () => this.goTo('denied'));
  }

  protected restart(): void {
    this.form.reset();
    this.request.set(null);
    this.error.set(null);
    this.goTo('enter');
  }

  /**
   * Le bouton qui a déclenché le changement disparaît avec l'étape : sans cela, le focus clavier
   * retomberait en haut de la page. On le place sur le titre de la nouvelle étape, que le lecteur
   * d'écran annonce.
   */
  private goTo(step: Step): void {
    this.step.set(step);
    afterNextRender(() => this.stepTitle()?.nativeElement.focus(), { injector: this.injector });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: (value) => {
        this.busy.set(false);
        onSuccess(value);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const status = e instanceof HttpErrorResponse ? e.status : 0;
        this.error.set(
          status === 404 ? "Code inconnu ou expiré. Vérifie le code affiché dans le jeu (il dure 10 minutes)."
          : status === 429 ? 'Trop de tentatives. Patiente une minute.'
          : status === 403 ? 'Session expirée. Recharge la page.'
          : 'Impossible de joindre le serveur.',
        );
      },
    });
  }
}
