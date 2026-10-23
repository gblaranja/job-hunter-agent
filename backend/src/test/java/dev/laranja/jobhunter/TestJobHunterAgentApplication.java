package dev.laranja.jobhunter;

import org.springframework.boot.SpringApplication;

public class TestJobHunterAgentApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobHunterAgentApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
