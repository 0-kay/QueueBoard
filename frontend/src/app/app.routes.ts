import { Routes } from '@angular/router';
import { DashboardPage } from './pages/dashboard/dashboard-page';
import { JobsChild, JobsPage } from './pages/jobs/jobs-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: DashboardPage, title: 'Dashboard · QueueBoard' },
  {
    path: 'jobs',
    component: JobsPage,
    title: 'Jobs · QueueBoard',
    children: [
      { path: '', component: JobsChild },
      { path: ':id', component: JobsChild },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
