import { createBrowserRouter, Navigate } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { LoginPage } from '@/features/auth/LoginPage'
import { AppLayout } from '@/layouts/AppLayout'

// Cada tela é carregada sob demanda, para o primeiro acesso baixar só o necessário.
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/board" replace /> },
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
