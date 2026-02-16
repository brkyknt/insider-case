package com.baykanat.insider.assessment;

import org.springframework.boot.SpringApplication;

public class TestAssessmentApplication {

	public static void main(String[] args) {
		SpringApplication.from(AssessmentApplication::main)
				.run(args);
	}

}
