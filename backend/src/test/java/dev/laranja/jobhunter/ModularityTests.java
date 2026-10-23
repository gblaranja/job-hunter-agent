package dev.laranja.jobhunter;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Garante que os módulos só se falam pela API pública ou por eventos, e gera os diagramas da arquitetura. */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(JobHunterAgentApplication.class);

	@Test
	void verifiesModuleBoundaries() {
		this.modules.verify();
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(this.modules).writeDocumentation();
	}

}
