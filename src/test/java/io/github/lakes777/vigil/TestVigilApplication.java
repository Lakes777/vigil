package io.github.lakes777.vigil;

import org.springframework.boot.SpringApplication;

public class TestVigilApplication {

	public static void main(String[] args) {
		SpringApplication.from(VigilApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
