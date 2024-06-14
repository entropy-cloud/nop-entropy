/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.spring.security;

import io.nop.spring.web.filter.SpringHttpServerFilterConfiguration;
import jakarta.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SpringSecurityConfig {
    @Inject
    SpringHttpServerFilterConfiguration config;

    @Bean
    public PermissionEvaluator permissionEvaluator() {
        return new SpringPermissionEvaluator();
    }

    @Bean
    public DefaultMethodSecurityExpressionHandler expressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator());
        return handler;
    }

    @Bean
    public WebAuthFilter authFilter() {
        return new WebAuthFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers("/r/**").authenticated()
                .requestMatchers("/p/**").authenticated()
                .requestMatchers("/f/**").authenticated()
                .requestMatchers("/graphql").authenticated()
                .requestMatchers("/error").permitAll();

        http
                .csrf(Customizer.withDefaults())
                .addFilterAfter(authFilter(), CsrfFilter.class)
                // 将Nop Context的初始化放到authFilter之前
                .addFilterBefore(config.registerSysFilter().getFilter(), WebAuthFilter.class);
        return http.build();
    }

}
