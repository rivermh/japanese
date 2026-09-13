package com.japanese.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/quiz/session/**", "/api/v1/quizzes/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/quiz/**", "/api/v1/quiz/**", "/grammars/**", "/api/v1/grammars/**").authenticated()
                        .requestMatchers("/grammars/*/confirm").authenticated()
                        .requestMatchers("/api/v1/grammars/weaknesses").authenticated()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/signup", "/login", "/email-verification", "/email-verification/resend", "/verify-email", "/forgot-password", "/reset-password", "/dev/account-mail", "/", "/dictionary", "/contents/**", "/categories", "/quiz/**", "/grammars/**", "/api/v1/contents/**", "/api/v1/quiz/questions/**", "/api/v1/grammars/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/account/email-verification/request", "/api/v1/account/email-verification/confirm", "/api/v1/account/password-reset/request", "/api/v1/account/password-reset/complete").permitAll()
                        .requestMatchers("/review/**").hasRole("ADMIN")
                        .requestMatchers("/study/**", "/bookmarks/**", "/statistics/**", "/progress/**", "/onboarding/**", "/api/v1/study/**", "/api/v1/onboarding/**", "/api/v1/progress/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .build();
    }
}
