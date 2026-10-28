package dev.laranja.jobhunter.jobs.internal;

import dev.laranja.jobhunter.jobs.JobFamily;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobFamilyClassifierTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Desenvolvedor Java Sênior                       | BACKEND
			Pessoa Engenheira de Software Back-end (Kotlin) | BACKEND
			Senior Backend Engineer, Data Platform          | BACKEND
			Desenvolvedor .NET Pleno                        | BACKEND
			Desenvolvedora Front-end React                  | FRONTEND
			Angular Developer                               | FRONTEND
			Desenvolvedor Java e React                      | FULLSTACK
			Full Stack Engineer (Node/React)                | FULLSTACK
			Desenvolvedor React Native                      | MOBILE
			Senior iOS Engineer                             | MOBILE
			Engenheiro de Dados Sênior                      | DADOS
			Cientista de Dados                              | DADOS
			Machine Learning Engineer                       | DADOS
			Analista de BI                                  | DADOS
			SRE Pleno                                       | DEVOPS_SRE
			Engenheiro DevOps                               | DEVOPS_SRE
			Analista de Infraestrutura Cloud                | DEVOPS_SRE
			QA Automation Engineer                          | QA
			Analista de Testes                              | QA
			Analista de Segurança da Informação             | SEGURANCA
			Engineering Manager                             | GESTAO
			Head of Data                                    | GESTAO
			Coordenador de TI                               | GESTAO
			Gerente Comercial                               | FORA_DE_TI
			Vendedor Interno                                | FORA_DE_TI
			Analista Financeiro                             | FORA_DE_TI
			Customer Success Manager                        | FORA_DE_TI
			Recrutadora de RH                               | FORA_DE_TI
			Engenheiro de Software                          | OUTRA
			Analista de Suporte Técnico                     | OUTRA
			""")
	void classifiesByTitle(String title, JobFamily expected) {
		assertThat(JobFamilyClassifier.classify(title)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "Vaga 123", "Oportunidade" })
	void unknownTitleHasNoFamily(String title) {
		assertThat(JobFamilyClassifier.classify(title)).isNull();
	}

}
