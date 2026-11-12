import { inject, Injectable, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import { ReferenceApiService } from '../api/reference-api.service';
import { JobTypeInfo, ReferenceData } from '../api/models';

/** Caches reference data (types, courses, terms, templates) after the first good load. */
@Injectable({ providedIn: 'root' })
export class ReferenceDataService {
  private readonly api = inject(ReferenceApiService);
  private cache: ReferenceData | null = null;
  readonly jobTypes = signal<JobTypeInfo[] | null>(null);

  load(force = false): Observable<ReferenceData> {
    if (this.cache && !force) return of(this.cache);
    return this.api.all().pipe(
      tap((data) => {
        this.cache = data;
        this.jobTypes.set(data.jobTypes);
      }),
    );
  }

  loadJobTypes(): void {
    if (this.jobTypes()) return;
    this.api.jobTypes().subscribe({ next: (t) => this.jobTypes.set(t), error: () => undefined });
  }
}
