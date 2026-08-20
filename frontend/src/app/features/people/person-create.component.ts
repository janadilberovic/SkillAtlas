import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PeopleApi } from '../../core/api/api';
import { Role } from '../../core/models/models';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';

const MIN_PASSWORD = 8;

/** Admin create (E2.1). Team and picture are not here — a person is created first, then assigned. */
@Component({
  selector: 'sa-person-create',
  standalone: true,
  imports: [FormsModule, SelectComponent],
  templateUrl: './person-create.component.html',
  styleUrl: './person-create.component.css',
})
export class PersonCreateComponent {
  private readonly api = inject(PeopleApi);

  readonly closed = output<boolean>();

  readonly roleOptions: SelectOption[] = [
    { value: 'MEMBER', label: 'Member' },
    { value: 'ADMIN', label: 'Admin' },
  ];

  firstName = '';
  lastName = '';
  email = '';
  password = '';
  position = '';
  role = '';

  readonly submitting = signal(false);
  readonly error = signal('');

  emailInvalid(): boolean {
    return this.email.trim() !== '' && !this.emailLooksValid();
  }

  passwordShort(): boolean {
    return this.password !== '' && this.password.length < MIN_PASSWORD;
  }

  previewName(): string {
    return `${this.firstName} ${this.lastName}`.trim();
  }

  canSubmit(): boolean {
    return (
      this.emailLooksValid() &&
      this.password.length >= MIN_PASSWORD &&
      this.firstName.trim() !== '' &&
      this.lastName.trim() !== '' &&
      this.role !== '' &&
      !this.submitting()
    );
  }

  create(): void {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set('');
    this.api
      .create({
        email: this.email.trim(),
        password: this.password,
        firstName: this.firstName.trim(),
        lastName: this.lastName.trim(),
        position: this.position.trim() || undefined,
        role: this.role as Role,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.closed.emit(true);
        },
        // 409 on a taken email is the common case, and the server's sentence is the useful one.
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.error ?? 'Could not create the person. Check the API and try again.');
        },
      });
  }

  private emailLooksValid(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(this.email.trim());
  }
}
