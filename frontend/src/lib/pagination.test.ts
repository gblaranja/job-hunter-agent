import { pageWindow } from './pagination'

describe('pageWindow', () => {
  it('mostra todas as páginas quando cabem', () => {
    expect(pageWindow(0, 1)).toEqual([0])
    expect(pageWindow(2, 7)).toEqual([0, 1, 2, 3, 4, 5, 6])
  })

  it('troca os saltos por reticências mantendo primeira, última e vizinhas', () => {
    expect(pageWindow(0, 12)).toEqual([0, 1, 2, 3, 4, 'gap', 11])
    expect(pageWindow(6, 12)).toEqual([0, 'gap', 5, 6, 7, 'gap', 11])
    expect(pageWindow(11, 12)).toEqual([0, 'gap', 7, 8, 9, 10, 11])
  })
})
