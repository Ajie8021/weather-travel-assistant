package com.example.wea_trav_assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.wea_trav_assistant.config.AgentProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class WeaTravAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeaTravAssistantApplication.class, args);
	}

}
