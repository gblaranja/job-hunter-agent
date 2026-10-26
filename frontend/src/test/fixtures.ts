import type { AppliedJob, AppliedListItem, Board, JobListItem, ResumeItem, TailoredResume } from '@/api/queries'

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
    skills: ['Java', 'Spring Boot'],
    hasText: true,
    lint: [],
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
    hasResponse: false,
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
            nextActionKind: null,
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
            nextActionKind: 'FOLLOW_UP',
            linkStatus: 'REMOVIDO',
          },
        ],
      },
      { status: 'ENTREVISTA', cards: [] },
    ],
  }
}

export function inboxJob(overrides: Partial<JobListItem> = {}): JobListItem {
  return {
    id: 30,
    title: 'Desenvolvedor Java Sênior',
    company: 'Banco Exemplo',
    companyId: 5,
    location: 'São Paulo, SP (Remoto)',
    workModel: 'REMOTO',
    scope: 'NACIONAL',
    url: 'https://www.linkedin.com/jobs/view/4012345678/',
    origin: 'LINKEDIN_ALERTA',
    collectedAt: '2026-10-23T10:00:00Z',
    firstSeenAt: '2026-10-23T10:00:00Z',
    legitimacy: 'ALTA',
    legitimacyReasons: [],
    preview: 'Buscamos pessoa desenvolvedora backend com Java e Kafka.',
    applicationId: null,
    status: 'NOVA',
    rating: null,
    reasons: [],
    matchScore: 82,
    summary: 'Evoluir o motor de crédito em Java com eventos em Kafka.',
    matchedSkills: ['Java', 'Kafka'],
    gaps: ['AWS'],
    failedKnockouts: 0,
    ...overrides,
  }
}

export function tailoredResume(overrides: Partial<TailoredResume> = {}): TailoredResume {
  return {
    id: 5,
    jobId: 20,
    baseResumeId: 1,
    baseResumeLabel: 'Backend Java',
    status: 'PRONTO',
    content: '# Ana Lima\n## Resumo\nDesenvolvedora backend Java com Spring Boot, Kafka e AWS.',
    headline: 'Desenvolvedora Backend Java Sênior',
    changes: [
      {
        section: 'Resumo',
        before: 'Desenvolvedora Java.',
        after: 'Desenvolvedora backend Java com Spring Boot.',
        reason: 'A vaga pede Spring Boot.',
      },
    ],
    notCovered: ['Kubernetes'],
    notes: 'Preencha o volume de transações.',
    keywords: ['Java', 'Spring Boot', 'Kafka', 'AWS', 'Kubernetes'],
    coveredBefore: ['Java', 'Spring Boot', 'Kafka'],
    coveredAfter: ['Java', 'Spring Boot', 'Kafka', 'AWS'],
    unsupportedSkills: ['AWS'],
    scoreBefore: 68,
    scoreAfter: 85,
    edited: false,
    resumeVersionId: null,
    resumeLabel: null,
    modelVersion: 'claude-opus-5-5|tailor-resume@v2',
    lastError: null,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:02:00Z',
    ...overrides,
  }
}
