import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import {
  AuthApi,
  FinderApi,
  GraphApi,
  PeopleApi,
  PeopleSkillsApi,
  ProjectApi,
  SkillApi,
  TeamApi,
} from './core/api/api';
import {
  HttpAuthApi,
  HttpFinderApi,
  HttpGraphApi,
  HttpPeopleApi,
  HttpPeopleSkillsApi,
  HttpProjectApi,
  HttpSkillApi,
  HttpTeamApi,
} from './core/api/http-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([authInterceptor])),

    // --- API seam (live) -------------------------------------------------
    // Features with a real backend are bound to their Http* implementations.
    // Graph / Dashboard have no backend yet — their routes render the
    // "waiting for the API" screen, so no API binding is needed for them.
    { provide: AuthApi, useClass: HttpAuthApi },
    { provide: FinderApi, useClass: HttpFinderApi },
    { provide: GraphApi, useClass: HttpGraphApi },
    { provide: PeopleApi, useClass: HttpPeopleApi },
    { provide: PeopleSkillsApi, useClass: HttpPeopleSkillsApi },
    { provide: SkillApi, useClass: HttpSkillApi },
    { provide: ProjectApi, useClass: HttpProjectApi },
    { provide: TeamApi, useClass: HttpTeamApi },
  ],
};
