package dev.laranja.jobhunter.inbox.internal;

import dev.laranja.jobhunter.inbox.EmailClassification;
import dev.laranja.jobhunter.shared.ai.AiProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseRulesTest {

	private final ResponseClassifier classifier = new ResponseClassifier(null, new AiProperties(null, null, null));

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Atualização do processo  | Infelizmente seguimos com outro candidato.          | RECUSA
			Próxima etapa            | Gostaríamos de agendar uma entrevista com você.      | CONVITE_ENTREVISTA
			Desafio                  | Segue o desafio técnico, prazo de 5 dias.            | TESTE_TECNICO
			Proposta                 | Temos uma proposta de trabalho para você.            | OFERTA
			Sua candidatura          | Qual sua pretensão salarial?                         | PEDIDO_INFORMACAO
			Newsletter               | Confira as novidades do mês.                         | OUTRO
			Entrevista               | Infelizmente não seguiremos após a entrevista.       | RECUSA
			Recebemos sua candidatura | Se avançar, chamaremos para entrevista.             | CONFIRMACAO_CANDIDATURA
			Obrigado                 | Obrigada por se candidatar à vaga de Java.           | CONFIRMACAO_CANDIDATURA
			Application received     | Thank you for applying to Acme.                      | CONFIRMACAO_CANDIDATURA
			Oportunidade Java        | Vi seu perfil no LinkedIn e tenho uma oportunidade.  | CONTATO_RECRUTADOR
			Opportunity              | I came across your profile and have a role.          | CONTATO_RECRUTADOR
			""")
	void rulesClassifyWithLowConfidence(String subject, String body, EmailClassification expected) {
		ResponseClassifier.Result result = this.classifier.byRules(subject, body);

		assertThat(result.classification()).isEqualTo(expected);
		assertThat(result.confidence()).isLessThan(0.7);
	}

}
