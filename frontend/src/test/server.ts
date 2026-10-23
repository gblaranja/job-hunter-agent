import { setupServer } from 'msw/node'

/** Servidor MSW compartilhado; cada teste registra as respostas de que precisa com server.use(). */
export const server = setupServer()
