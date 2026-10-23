import type { AppliedJob, AppliedListItem, Board, ResumeItem } from '@/api/queries'

export function resume(overrides: Partial<ResumeItem> = {}): ResumeItem {
  return {
    id: 1,
    label: 'Backend Java',
    targetRole: 'Desenvolvedor Java Sênior',
    fileName: 'cv-backend.pdf',
    contentType: 'application/pdf',
    sizeBytes: 120_000,
    isDefault: true,
    archived: false,
    createdAt: '2026-10-01T12:00:00Z',
    applications: 3,
    interviews: 1,
    interviewRate: 1 / 3,
    ...overrides,
  }
}

export function appliedJob(overrides: Partial<AppliedJob['snapshot']> = {}): AppliedJob {
  return {
    applicationId: 10,
    jobId: 20,
    status: 'APLICADA',
    appliedAt: '2026-10-10T12:00:00Z',
    channel: 'SITE_EMPRESA',
    resume: { id: 1, label: 'Backend Java', fileName: 'cv-backend.pdf' },
    snapshot: {
      url: 'https://empresa.example/vagas/42',
      linkStatus: 'ATIVO',
      linkCheckedAt: null,
      title: 'Desenvolvedor Java Sênior',
      company: 'Banco Exemplo',
      descriptionMd: '## Requisitos\n- Java 21\n- Apache Kafka',
      captureSource: 'MANUAL',
      hasPagePdf: false,
      capturedAt: '2026-10-10T12:00:00Z',
      ...overrides,
    },
  }
}

export function appliedListItem(overrides: Partial<AppliedListItem> = {}): AppliedListItem {
  return {
    applicationId: 10,
    jobId: 20,
    title: 'Desenvolvedor Java Sênior',
    company: 'Banco Exemplo',
    status: 'ENTREVISTA',
    appliedAt: '2026-10-10T12:00:00Z',
    channel: 'SITE_EMPRESA',
    url: 'https://empresa.example/vagas/42',
    linkStatus: 'NAO_VERIFICADO',
    resumeVersionId: 1,
    resumeLabel: 'Backend Java',
    excerpt: null,
    ...overrides,
  }
}

export function board(): Board {
  return {
    columns: [
      {
        status: 'INTERESSE',
        cards: [
          {
            applicationId: 1,
            jobId: 11,
            title: 'Engenheiro de Software',
            company: 'Fintech Exemplo',
            status: 'INTERESSE',
            daysInStatus: 0,
            resumeVersionId: null,
            resumeLabel: null,
            nextActionAt: null,
            linkStatus: null,
          },
        ],
      },
      {
        status: 'APLICADA',
        cards: [
          {
            applicationId: 2,
            jobId: 12,
            title: 'Desenvolvedor Java Sênior',
            company: 'Banco Exemplo',
            status: 'APLICADA',
            daysInStatus: 4,
            resumeVersionId: 1,
            resumeLabel: 'Backend Java',
            nextActionAt: '2026-01-01T12:00:00Z',
            linkStatus: 'REMOVIDO',
          },
        ],
      },
      { status: 'ENTREVISTA', cards: [] },
    ],
  }
}
