package dev.laranja.jobhunter.shared;

/** Uma regra de negócio impediu a operação (por exemplo, aplicar sem currículo); vira 422. */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}

}
