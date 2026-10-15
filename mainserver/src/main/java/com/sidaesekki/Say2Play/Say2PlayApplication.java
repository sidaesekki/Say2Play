package com.sidaesekki.Say2Play;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Say2PlayApplication {

	public static void main(String[] args) {
		SpringApplication.run(Say2PlayApplication.class, args);
	}

}
