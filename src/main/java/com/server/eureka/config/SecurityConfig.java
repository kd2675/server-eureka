//package com.server.eureka.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.provisioning.InMemoryUserDetailsManager;
//import org.springframework.security.web.SecurityFilterChain;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
//    @Value("${eureka.security.admin.username:admin}")
//    private String adminUsername;
//
//    @Value("${eureka.security.admin.password:admin123}")
//    private String adminPassword;
//
//    @Value("${eureka.security.client.username:eureka}")
//    private String clientUsername;
//
//    @Value("${eureka.security.client.password:eureka123}")
//    private String clientPassword;
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//            .csrf().disable()  // Eureka는 CSRF 불필요
//            .authorizeHttpRequests(authz -> authz
//                // ✅ 헬스체크는 인증 불필요
//                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
//
//                // ✅ Eureka 클라이언트 API는 클라이언트 권한 필요
//                .requestMatchers("/eureka/apps/**").hasRole("CLIENT")
//
//                // ✅ 관리자 대시보드는 관리자 권한 필요
//                .requestMatchers("/", "/eureka/**", "/admin/**").hasRole("ADMIN")
//
//                // ✅ Prometheus 메트릭은 모니터링 권한 필요
//                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "MONITOR")
//
//                .anyRequest().authenticated()
//            )
//            .httpBasic();  // Basic Authentication 사용
//
//        return http.build();
//    }
//
//    @Bean
//    public UserDetailsService userDetailsService() {
//        // 관리자 계정
//        UserDetails admin = User.builder()
//            .username(adminUsername)
//            .password(passwordEncoder().encode(adminPassword))
//            .roles("ADMIN", "CLIENT", "MONITOR")
//            .build();
//
//        // 클라이언트 계정
//        UserDetails client = User.builder()
//            .username(clientUsername)
//            .password(passwordEncoder().encode(clientPassword))
//            .roles("CLIENT")
//            .build();
//
//        // 모니터링 계정
//        UserDetails monitor = User.builder()
//            .username("monitor")
//            .password(passwordEncoder().encode("monitor123"))
//            .roles("MONITOR")
//            .build();
//
//        return new InMemoryUserDetailsManager(admin, client, monitor);
//    }
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//}