package com.baykanat.insider.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Uygulama giriş noktası; @EnableScheduling ile MV refresh ve inbox cleanup. */
@SpringBootApplication
@EnableScheduling
public class AssessmentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AssessmentApplication.class, args);
	}

}
