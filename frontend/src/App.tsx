import { createBrowserRouter, Navigate } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { LoginPage } from '@/features/auth/LoginPage'
import { HomePage } from '@/features/home/HomePage'
import { AppLayout } from '@/layouts/AppLayout'

// Cada tela é carregada sob demanda, para o primeiro acesso baixar só o necessário.
const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/login', element: <LoginPage /> },
  {
    element: <AppLayout />,
    children: [
      {
        path: 'inbox',
        lazy: () => import('@/features/inbox/InboxPage').then((module) => ({ Component: module.InboxPage })),
      },
      {
        path: 'responses',
        lazy: () =>
          import('@/features/responses/ResponsesPage').then((module) => ({ Component: module.ResponsesPage })),
      },
      {
        path: 'companies',
        lazy: () =>
          import('@/features/companies/CompaniesPage').then((module) => ({ Component: module.CompaniesPage })),
      },
      {
        path: 'companies/:companyId',
        lazy: () =>
          import('@/features/companies/CompanyDetailPage').then((module) => ({ Component: module.CompanyDetailPage })),
      },
      {
        path: 'niches',
        lazy: () => import('@/features/niches/NichesPage').then((module) => ({ Component: module.NichesPage })),
      },
      {
        path: 'insights',
        lazy: () => import('@/features/insights/InsightsPage').then((module) => ({ Component: module.InsightsPage })),
      },
      {
        path: 'settings',
        lazy: () => import('@/features/settings/SettingsPage').then((module) => ({ Component: module.SettingsPage })),
      },
      {
        path: 'board',
        lazy: () => import('@/features/board/BoardPage').then((module) => ({ Component: module.BoardPage })),
      },
      {
        path: 'applications',
        lazy: () =>
          import('@/features/applications/ApplicationsPage').then((module) => ({
            Component: module.ApplicationsPage,
          })),
      },
      {
        path: 'jobs/new',
        lazy: () => import('@/features/jobs/NewJobPage').then((module) => ({ Component: module.NewJobPage })),
      },
      {
        path: 'jobs/:jobId',
        lazy: () => import('@/features/jobs/JobDetailPage').then((module) => ({ Component: module.JobDetailPage })),
      },
      {
        path: 'resumes',
        lazy: () => import('@/features/resumes/ResumesPage').then((module) => ({ Component: module.ResumesPage })),
      },
      { path: '*', element: <Navigate to="/board" replace /> },
    ],
  },
])

export function App() {
  return <RouterProvider router={router} />
}
