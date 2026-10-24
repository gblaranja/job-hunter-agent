-- Os dois primeiros nichos: Financeiro (fintechs, bancos e instituições financeiras) e Consultorias de tecnologia.
-- As palavras-chave são comparadas sem acento e sem caixa; os termos de domínio ficam com acento porque aparecem na
-- justificativa do fit. Os tipos de autorização vêm do Banco Central e os CNAEs da Receita, como nas importações.

insert into niche (name, description, fit_weights, company_kinds, domain_terms) values
    ('Financeiro', 'Fintechs, bancos e instituições financeiras.',
     '{"segment": 0.35, "stack": 0.30, "domain": 0.20, "signals": 0.15}', '{CONTRATANTE}',
     '{crédito,antifraude,fraude,motor de crédito,onboarding,KYC,Open Finance,Pix}'),
    ('Consultorias', 'Consultorias de tecnologia que alocam profissionais ou entregam projetos para clientes.',
     '{"segment": 0.35, "stack": 0.30, "domain": 0.20, "signals": 0.15}', '{CONSULTORIA_TI}',
     '{crédito,antifraude,fraude,motor de crédito,onboarding,KYC,Open Finance,Pix,banco,bancos,fintech,fintechs,pagamentos,meios de pagamento}');

insert into niche_segment (niche_id, name, description, base_fit, keywords, authorization_types)
select n.id, s.name, s.description, s.base_fit, s.keywords, s.authorization_types
from niche n
join (values
    ('Financeiro', 'Crédito', 'SCD, SEP, financeiras, consignado e crédito PJ.', 90,
     '{credito,emprestimo,emprestimos,consignado,financiamento,financiamentos,scd,lending,credit,financeira}'::text[],
     '{Sociedade de Crédito Direto,Sociedade de Empréstimo entre Pessoas,"Sociedade de Crédito, Financiamento e Investimento",Sociedade de Crédito ao Microempreendedor,Companhia Hipotecária,Sociedade de Arrendamento Mercantil,Associação de Poupança e Empréstimo}'::text[]),
    ('Financeiro', 'Bancos digitais e múltiplos', 'Bancos comerciais, múltiplos e digitais.', 85,
     '{banco,bancos,bank,banco digital,neobank,instituicao financeira}',
     '{Banco Múltiplo,Banco Comercial,Banco Comercial Estrangeiro - Filial no país,Banco de Câmbio,Banco Múltiplo Cooperativo,Banco do Brasil - Banco Múltiplo,Caixa Econômica Federal,Banco de Desenvolvimento,BNDES,Agência de Fomento}'),
    ('Financeiro', 'Antifraude, risco e bureaus de crédito', 'Bureaus, motores de decisão e prevenção a fraude.', 90,
     '{antifraude,fraude,fraudes,bureau,biro,score de credito,risco de credito,analise de credito,prevencao a fraude,motor de decisao}',
     '{}'),
    ('Financeiro', 'Cooperativas de crédito', 'Sistemas cooperativos, centrais e singulares.', 75,
     '{cooperativa,cooperativas,cooperativismo,sicredi,sicoob,unicred,cresol,ailos}',
     '{Cooperativa de Crédito}'),
    ('Financeiro', 'Pagamentos e adquirência', 'Instituições de pagamento, adquirentes, subadquirentes, Pix e cartões.', 65,
     '{pagamento,pagamentos,adquirencia,adquirente,subadquirente,gateway de pagamento,pix,cartao,cartoes,maquininha,payments}',
     '{Instituição de Pagamento}'),
    ('Financeiro', 'Open Finance, BaaS e infraestrutura', 'Banking as a Service e conectores de Open Finance.', 65,
     '{open finance,open banking,baas,banking as a service,infraestrutura financeira,embedded finance}',
     '{}'),
    ('Financeiro', 'Investimentos e corretoras', 'Corretoras, gestoras e plataformas de investimento.', 45,
     '{investimento,investimentos,corretora,gestora,asset,dtvm,ctvm,wealth,renda fixa,mercado de capitais}',
     '{Sociedade Distribuidora de TVM,Sociedade Corretora de TVM,Sociedade Corretora de Câmbio,Banco de Investimento}'),
    ('Financeiro', 'Seguros e consórcios', 'Seguradoras, insurtechs e administradoras de consórcio.', 40,
     '{seguro,seguros,seguradora,insurtech,consorcio,consorcios,previdencia}',
     '{Administradora de Consórcio}'),
    ('Consultorias', 'Alocação e outsourcing', 'Profissionais alocados em times do cliente, PJ ou CLT.', 70,
     '{alocacao,alocado,alocada,outsourcing,body shop,bodyshop,cliente final,staff augmentation,alocacao em cliente}',
     '{CNAE 6209-1/00}'),
    ('Consultorias', 'Fábrica de software e squads', 'Projetos entregues por times da própria consultoria.', 65,
     '{fabrica de software,squad as a service,squads,software sob encomenda,software house,projetos para clientes}',
     '{CNAE 6201-5/01}'),
    ('Consultorias', 'Integradoras globais', 'Grandes multinacionais de serviços de TI.', 60,
     '{accenture,capgemini,ibm,tcs,tata consultancy,infosys,wipro,ntt data,deloitte,cognizant,globant,stefanini,dxc,atos,kyndryl,hcl}',
     '{}'),
    ('Consultorias', 'Consultorias especializadas', 'Foco num domínio: dados, cloud, segurança ou financeiro.', 70,
     '{consultoria especializada,consultoria de dados,consultoria cloud,ciberseguranca,seguranca da informacao,engenharia de dados,consultoria financeira}',
     '{CNAE 6204-0/00}')
) as s (niche, name, description, base_fit, keywords, authorization_types) on s.niche = n.name;
