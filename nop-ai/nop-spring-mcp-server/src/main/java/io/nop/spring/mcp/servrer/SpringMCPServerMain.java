package io.nop.spring.mcp.servrer;

import io.nop.core.initialize.CoreInitialization;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringMCPServerMain {
    public static void main(String[] args) {
        CoreInitialization.initialize();
        SpringApplication.run(SpringMCPServerMain.class, args);
    }
}