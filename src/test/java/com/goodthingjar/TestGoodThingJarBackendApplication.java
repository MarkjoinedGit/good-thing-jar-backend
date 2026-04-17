package com.goodthingjar;

import org.springframework.boot.SpringApplication;

public class TestGoodThingJarBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(GoodThingJarBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
