package io.nop.demo.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringSecurityDemoMain {
    public static void main(String[] args) {
        System.out.println("maxMem=" + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "M");
        SpringApplication.run(SpringSecurityDemoMain.class, args);
    }
}