import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'sa-sign-in',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './sign-in.component.html',
  styleUrl: './sign-in.component.css',
})
export class SignInComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = 'admin@skillatlas.dev';
  password = 'Password123!';
  readonly error = signal('');
  readonly loading = signal(false);

  submit(): void {
    this.error.set('');
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/']);
      },
      error: (e: Error) => {
        this.loading.set(false);
        this.error.set(e.message || 'Incorrect email or password.');
      },
    });
  }
}
