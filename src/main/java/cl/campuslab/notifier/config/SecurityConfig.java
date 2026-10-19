package cl.campuslab.notifier.config;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        JwtGrantedAuthoritiesConverter rolesConverter =
                new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                rolesConverter
        );

        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Conserva el código de las respuestas
                        // durante el despacho interno de errores.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/audit",
                                "/api/audit/**"
                        )
                        .hasAnyRole("Admin", "Auditor")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/notifications",
                                "/api/notifications/**"
                        )
                        .hasAnyRole("Admin", "Operador", "Cliente")

                        .anyRequest()
                        .denyAll()
                )

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        authenticationConverter
                                )
                        )
                )

                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer,

            @Value("${campuslab.security.audience}")
            String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withIssuerLocation(issuer)
                .build();

        JwtClaimValidator<List<String>> audienceValidator =
                new JwtClaimValidator<>(
                        "aud",
                        audiences -> audiences != null
                                && audiences.contains(audience)
                );

        JwtClaimValidator<String> scopeValidator =
                new JwtClaimValidator<>(
                        "scp",
                        scopes -> scopes != null
                                && Arrays.asList(
                                        scopes.split("\\s+")
                                ).contains("access_as_user")
                );

        JwtClaimValidator<Object> expirationRequired =
                new JwtClaimValidator<>(
                        "exp",
                        value -> value != null
                );

        JwtClaimValidator<String> ownerRequired =
                new JwtClaimValidator<>(
                        "oid",
                        value -> value != null
                                && !value.isBlank()
                );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<Jwt>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audienceValidator,
                        scopeValidator,
                        expirationRequired,
                        ownerRequired
                )
        );

        return decoder;
    }
}