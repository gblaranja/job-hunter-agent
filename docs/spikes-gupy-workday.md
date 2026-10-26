# Spikes: Gupy (B-37) e Workday (B-38)

Feitos em 24/10/2026, só com leitura e poucas requisições: 6 páginas da Gupy (`stone`, `itau`, `picpay`, `localiza`, `ambev` e uma vaga da `localiza`) e 5 chamadas ao Workday (Nvidia, Salesforce e Pfizer). Nenhum adapter de coleta foi implementado.

## Gupy (`{empresa}.gupy.io`)

**O que a página expõe**
- A página de carreira é um app Next.js renderizado no servidor. O `<script id="__NEXT_DATA__">` traz `props.pageProps.jobs` com todas as vagas abertas: `id`, `title`, `type`, `department`, `workplace.address` (cidade, estado, país) e `workplace.workplaceType` (`on-site`, `hybrid`, `remote`). A lista não traz a descrição. A Localiza tinha 867 vagas e a Ambev 299. `stone` respondeu sem vagas, e `itau` e `picpay` responderam 404 (o subdomínio não é o nome da empresa, ou a empresa não usa a página da Gupy).
- A página de cada vaga (`{empresa}.gupy.io/jobs/{id}`) tem um JSON-LD `JobPosting` completo, com a descrição. O JSON vem **escapado como entidades HTML** dentro do `<script>` (`{&quot;@context&quot;...`): quem lê precisa decodificar as entidades antes do `JSON.parse`. A extensão faz isso; o importador por link da Fase D precisa fazer o mesmo.
- O `robots.txt` permite tudo, menos `/companies` e `/candidates`, e aponta um sitemap em `job-boards.api.gupy.io/production/job-board-content?jobBoardName=google&subdomain={empresa}`. Esse endereço redireciona para um XML num bucket S3 com URL assinada: é o feed que a Gupy gera para o Google for Jobs, não uma API pública.
- Não há API pública documentada. A API oficial é autenticada e serve às empresas.

**O que os termos dizem**
Os [termos de uso para pessoas candidatas](https://www.gupy.io/termos-de-uso-recrutamento-e-selecao-candidatos), em "Diretrizes Gerais do Usuário", proíbem "agregar, copiar ou duplicar partes do Gupy Recrutamento e Seleção, incluindo oportunidades de trabalho expiradas". Não citam robôs nem scraping, mas uma coleta diária das vagas de cada empresa é agregação.

**Recomendação: não implementar o adapter.**
- A coleta automática (ler o `__NEXT_DATA__` todo dia) agrega vagas, contra a cláusula acima. Além disso, depende da estrutura interna de um app Next.js, que muda a cada deploy.
- O que já cobre a Gupy sem esse risco: os alertas por e-mail (`GUPY_ALERTA`) e a importação de uma vaga por link ou pela extensão "Salvar vaga", que lê o JSON-LD da página aberta, com um clique (D-03).
- Se for preciso cobrir mais, o caminho é pedir à Gupy uma autorização ou um feed, não fazer engenharia reversa.

## Workday (`/wday/cxs/{tenant}/{site}/jobs`)

**O que o endpoint expõe**
- `POST https://{tenant}.wd{n}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs` com `{"appliedFacets":{},"limit":20,"offset":0,"searchText":""}` respondeu 200 a partir deste IP para as três empresas: Nvidia (`total` 2000, que parece ser um teto), Salesforce (1571) e Pfizer (551). Cada item traz `title`, `externalPath`, `locationsText`, `postedOn` ("Posted Today", relativo) e `bulletFields` (o código da vaga). Não traz descrição.
- O detalhe (`GET /wday/cxs/{tenant}/{site}{externalPath}`) traz `jobPostingInfo.jobDescription` (HTML), `location`, `timeType`, `startDate` e `externalUrl`.
- A página pública da vaga tem um JSON-LD `JobPosting`.
- Para descobrir o endpoint, é preciso saber o tenant, o número do data center (`wd1`, `wd5`, `wd12`...) e o nome do site, que muda de empresa para empresa. Tudo isso sai da URL da página de carreira.

**Riscos**
- O endpoint é interno ao front do Workday: não tem documentação nem termos próprios, e pode mudar sem aviso.
- O discovery já registra empresas que bloqueiam IPs de datacenter. Daqui funcionou, mas não há garantia.
- O `total` limitado a 2000 e as datas relativas ("Posted Today") obrigam a paginar tudo e guardar a data de quando a vaga foi vista. Com várias empresas grandes, são centenas de requisições por dia só para listar, mais uma por vaga nova para a descrição.

**Recomendação: não implementar agora.** Não é "claramente permitido e estável". Para as empresas-alvo no Workday, use a importação por link ou a extensão (a página da vaga tem JSON-LD). Se o volume justificar mais tarde, o adapter seria opcional e desligado por padrão, como as fontes amplas: só empresas-alvo, detalhe só das vagas novas, `User-Agent` identificado, uma consulta por dia e o risco explicado no README.
