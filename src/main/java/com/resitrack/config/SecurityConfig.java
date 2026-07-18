package com.resitrack.config;

import com.resitrack.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final UserDetailsService      userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                
                .requestMatchers(
                    "/auth/login",                          
                    "/auth/admin/login",                    
                    "/auth/user/login",                     
                    "/auth/security/login",                
                    "/auth/register",
                    "/auth/validate-register-number/**",
                    "/auth/registration-status/**",
                    "/auth/refresh",
                    "/auth/logout"
                ).permitAll()

                // TEMPORARY — Brevo SMTP verification endpoint only (no auth
                // logic touched). Remove this line if/when the endpoint
                // itself is removed after Forgot Password ships.
                .requestMatchers(HttpMethod.POST, "/test/send-email").permitAll()

                .requestMatchers("/uploads/**").permitAll()

                
                .requestMatchers(HttpMethod.GET, "/members", "/members/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/members", "/members/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/members/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/members/**").hasRole("ADMIN")

                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                .requestMatchers("/security/**").hasRole("SECURITY")

                // Password change endpoints (unchanged)
                .requestMatchers("/auth/admin/change-password").hasRole("ADMIN")
                .requestMatchers("/auth/user/change-password").hasRole("USER")
                .requestMatchers("/auth/security/change-password").hasRole("SECURITY")

                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}