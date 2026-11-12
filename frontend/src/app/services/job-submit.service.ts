import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { JobApiService } from '../api/job-api.service';
import { toAppError } from '../api/app-error';
import { SubmitJobRequest } from '../api/models';
import { SubmitModal, SubmitPrefill } from '../components/submit-modal/submit-modal';
import { JobPollingService } from './job-polling.service';
import { NotificationService } from './notification.service';
import { ReferenceDataService } from './reference-data.service';
import { SessionJobsService } from './session-jobs.service';

/** Opens the submit modal (empty, or pre-filled for Duplicate) and drives its request. */
@Injectable({ providedIn: 'root' })
export class JobSubmitService {
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(JobApiService);
  private readonly reference = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly session = inject(SessionJobsService);
  private readonly polling = inject(JobPollingService);

  open(prefill?: SubmitPrefill): void {
    const ref = this.dialog.open(SubmitModal, {
      width: '560px',
      maxWidth: 'calc(100vw - 32px)',
      ariaLabelledBy: 'qb-create-title',
      autoFocus: 'first-tabbable',
    });
    const cmp = ref.componentRef!;
    cmp.setInput('prefill', prefill ?? null);

    this.reference.load().subscribe({
      next: (data) => cmp.setInput('reference', data),
      error: (err: unknown) => cmp.setInput('serverError', toAppError(err)),
    });

    const instance = ref.componentInstance;
    instance.cancel.subscribe(() => ref.close());
    instance.submitJob.subscribe((request: SubmitJobRequest) => {
      ref.disableClose = true;
      cmp.setInput('serverError', null);
      cmp.setInput('sending', true);
      this.api.submit(request).subscribe({
        next: (job) => {
          ref.disableClose = false;
          ref.close(job);
          this.session.track(job.id, job.name);
          this.notify.show('Job submitted');
          this.polling.refreshNow();
        },
        error: (err: unknown) => {
          ref.disableClose = false;
          cmp.setInput('sending', false);
          cmp.setInput('serverError', toAppError(err));
        },
      });
    });
  }
}
