package io.nop.spring.delta.beans;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import(NopBeansRegistrar.class)
@Configuration
public class NopBeansAutoConfiguration {
}
