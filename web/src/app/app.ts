import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Coquille de l'application : le routeur y affiche la page courante. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
