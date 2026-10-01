package com.example.ui.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClientRegistrationRepository clientRegistrationRepository)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2Login(withDefaults())
            // Local logout alone only drops this app's session; Keycloak's SSO cookie would
            // survive and silently re-authenticate on the next request. RP-initiated logout
            // calls the realm's end_session_endpoint so the Keycloak session ends too.
            .logout(logout -> logout.logoutSuccessHandler(
                    oidcLogoutSuccessHandler(clientRegistrationRepository)));
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository repository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(repository);
        // Must also be registered as a post logout redirect URI on the Keycloak client.
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
