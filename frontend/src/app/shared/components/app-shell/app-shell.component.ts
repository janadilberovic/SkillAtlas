import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { AvatarComponent } from '../avatar/avatar.component';

@Component({
  selector: 'sa-app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css',
})
export class AppShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly importNote = signal(false);

  shortName(): string {
    const u = this.auth.user();
    if (!u) return '';
    const [first, ...rest] = u.fullName.split(' ');
    return rest.length ? `${first} ${rest[rest.length - 1][0]}.` : first;
  }

  importVacaYay(): void {
    this.importNote.set(true);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
