import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiToken, CreatedToken, TokenService } from '../../core/token.service';

@Component({
  selector: 'app-tokens',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './tokens.html',
  styleUrl: './tokens.css',
})
export class Tokens implements OnInit {
  private readonly tokenService = inject(TokenService);

  protected readonly tokens = signal<ApiToken[]>([]);
  // Token en clair affiché une seule fois après création
  protected readonly justCreated = signal<CreatedToken | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly copied = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.refresh();
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.tokenService.create(this.form.getRawValue().label).subscribe({
      next: (created) => {
        this.justCreated.set(created);
        this.copied.set(false);
        this.form.reset();
        this.refresh();
      },
      error: (e) =>
        this.error.set(e.status === 409 ? 'Limite de tokens actifs atteinte. Révoque-en un.' : 'Création impossible.'),
    });
  }

  protected revoke(token: ApiToken): void {
    if (!confirm(`Révoquer « ${token.label} » ? Le mod qui l'utilise cessera d'envoyer des morts.`)) {
      return;
    }
    this.tokenService.revoke(token.id).subscribe({
      next: () => this.refresh(),
      error: () => this.error.set('Révocation impossible.'),
    });
  }

  protected async copy(): Promise<void> {
    const created = this.justCreated();
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.token);
      this.copied.set(true);
    } catch {
      this.error.set('Copie impossible : sélectionne le token et copie-le manuellement.');
    }
  }

  protected dismiss(): void {
    this.justCreated.set(null);
  }

  private refresh(): void {
    this.tokenService.list().subscribe({
      next: (list) => this.tokens.set(list),
      error: () => this.error.set('Impossible de charger les tokens.'),
    });
  }
}
