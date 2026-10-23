import { Lock, ShieldCheck, Sparkles } from 'lucide-react'

// Texto da página pública. Tudo aqui precisa existir no produto: nada de números, clientes ou promessas inventados.

/** Funcionalidades, na ordem e na largura do Bento. `proof` liga o bloco a uma prévia do produto. */
export const features = [
  {
    title: 'Score de aderência com IA',
    text: 'Cada vaga ganha um score de 0 a 100 contra o seu currículo: um cálculo determinístico por skills, senioridade e modelo de trabalho, ajustado pela Claude com uma justificativa curta.',
    span: 7,
    proof: 'score',
  },
  {
    title: 'Coleta automática de vagas',
    text: 'Lê os alertas na sua label do Gmail e consulta os job boards das empresas que você acompanha. Duplicatas e reposts são detectados sozinhos.',
    span: 5,
    proof: 'sources',
  },
  {
    title: 'Kanban de candidaturas',
    text: 'Da caixa de vagas ao "aplicada" com um arrastar. Ao aplicar, a vaga é congelada com o currículo enviado: você sabe exatamente o que mandou, mesmo depois que o anúncio sai do ar.',
    span: 5,
  },
  {
    title: 'Retornos lidos por você',
    text: 'Convites de entrevista, testes técnicos, ofertas e recusas são classificados no Gmail e movem o card sozinhos quando a confiança é alta. O que for duvidoso cai numa fila de revisão de um clique.',
    span: 7,
    proof: 'responses',
  },
  {
    title: 'Insights semanais',
    text: 'Todo domingo: as skills mais pedidas, suas lacunas, onde o funil trava e bullets do currículo reescritos para as vagas que você realmente quer.',
    span: 6,
  },
  {
    title: 'Radar de empresas',
    text: 'Volume e frequência de vagas por semana, tempo médio no ar, reposts e alertas quando uma empresa-alvo abre vaga ou volta a contratar.',
    span: 6,
  },
] as const

export const sources = [
  { channel: 'Alertas no Gmail', names: ['LinkedIn', 'Gupy'] },
  { channel: 'Job boards', names: ['Greenhouse', 'Lever', 'Ashby'] },
] as const

export const steps = [
  { title: 'Conecte', text: 'Seu Gmail em modo leitura, só na label que você escolher, e o seu currículo.' },
  { title: 'Receba', text: 'Todo dia às 7h, as vagas novas chegam filtradas e ordenadas por aderência.' },
  { title: 'Candidate-se', text: 'Escolha, aplique e acompanhe tudo num kanban com o histórico completo.' },
  { title: 'Ajuste', text: 'Os retornos e os insights mostram o que mudar no currículo e nos filtros.' },
] as const

export const principles = [
  {
    Icon: Sparkles,
    title: 'IA como reforço, não dependência',
    text: 'Sem chave da Claude API, tudo funciona com regras e dicionário. Com ela, a análise fica mais precisa, com custo visível por tarefa.',
  },
  {
    Icon: Lock,
    title: 'Seus dados ficam com você',
    text: 'Auto-hospedado: o banco, os currículos e os tokens do Gmail (cifrados com AES-GCM) ficam na sua instalação. O corpo dos e-mails não é guardado.',
  },
  {
    Icon: ShieldCheck,
    title: 'Histórico que não se perde',
    text: 'A linha do tempo das candidaturas é append-only: correções viram novos eventos, nunca edição do passado.',
  },
] as const

export const stack = [
  'Java 25',
  'Spring Boot 4.1',
  'Spring Modulith',
  'PostgreSQL 17',
  'Claude API',
  'React 19',
  'TypeScript',
  'Tailwind CSS',
  'Testcontainers',
] as const

export const roadmap =
  'Em construção: nichos (Financeiro e Consultorias) com fit por empresa, dados abertos do Banco Central e da Receita, e o selo GPTW verificado.'
