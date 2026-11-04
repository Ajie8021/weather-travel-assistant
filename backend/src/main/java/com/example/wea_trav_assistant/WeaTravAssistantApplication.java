package com.example.wea_trav_assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.wea_trav_assistant.config.AgentProperties;
import com.example.wea_trav_assistant.config.AmapProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentProperties.class, AmapProperties.class})
public class WeaTravAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeaTravAssistantApplication.class, args);
	}

}
