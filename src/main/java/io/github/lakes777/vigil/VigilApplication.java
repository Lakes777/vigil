package io.github.lakes777.vigil;

import org.springframework.boot.SpringApplication;
import java.security.Security;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "Vigil", version = "0.6",
		description = "Monitor de status dos meus projetos: cadastro de serviços, verificações e disponibilidade. "
				+ "Ler é público; alterar pede a chave de admin (botão Authorize)."))
// O botão "Authorize" do Swagger: a chave digitada lá vai como "Authorization: Bearer <chave>"
@SecurityScheme(name = "chave", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class VigilApplication {

	public static void main(String[] args) {
		// Fixa por quanto tempo o Java guarda as respostas do DNS, sem depender do ambiente
		// (algumas imagens Docker zeram isso). O FiltroDeEnderecos conta com esse cache.
		Security.setProperty("networkaddress.cache.ttl", "30");
		SpringApplication.run(VigilApplication.class, args);
	}

}
