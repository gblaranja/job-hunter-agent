import { planMove } from './planMove'

describe('planMove', () => {
  it('ignora soltar na mesma coluna', () => {
    expect(planMove({ status: 'APLICADA', resumeVersionId: 1 }, 'APLICADA')).toEqual({ kind: 'none' })
  })

  it('pede o currículo ao aplicar sem um registrado', () => {
    expect(planMove({ status: 'INTERESSE', resumeVersionId: null }, 'APLICADA')).toEqual({
      kind: 'confirm',
      requireResume: true,
      requireNote: false,
    })
  })

  it('reaproveita o currículo já registrado nas etapas seguintes', () => {
    expect(planMove({ status: 'APLICADA', resumeVersionId: 1 }, 'ENTREVISTA')).toEqual({ kind: 'move' })
  })

  it('desistir antes de aplicar não pede currículo', () => {
    expect(planMove({ status: 'INTERESSE', resumeVersionId: null }, 'DESISTI')).toEqual({ kind: 'move' })
  })

  it('reabrir uma candidatura encerrada pede uma nota', () => {
    expect(planMove({ status: 'RECUSADA', resumeVersionId: 1 }, 'ENTREVISTA')).toEqual({
      kind: 'confirm',
      requireResume: false,
      requireNote: true,
    })
  })
})
