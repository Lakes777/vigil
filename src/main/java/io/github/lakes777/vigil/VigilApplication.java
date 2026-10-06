package io.github.lakes777.vigil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "Vigil", version = "0.4",
		description = "Monitor de status dos meus projetos: cadastro de serviços, verificações e disponibilidade."))
public class VigilApplication {

	public static void main(String[] args) {
		SpringApplication.run(VigilApplication.class, args);
	}

}
