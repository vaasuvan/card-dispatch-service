package com.bank.digitalcore.carddispatch.adapter.partner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import org.springframework.security.web.SecurityFilterChain;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configures the non-web OAuth2 client-credentials manager used for carrier requests.
 */
@Configuration
public class OAuth2ClientConfiguration {
    /** Disables inbound login and authentication; partner OAuth2 remains outbound only. */
    @Bean
    SecurityFilterChain cardDispatchSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Provides the builder used by partner clients when Boot's RestClient auto-configuration is absent.
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder partnerRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Builds a reusable authorized-client manager for background and request-thread calls.
     */
    @Bean
    OAuth2AuthorizedClientManager oauth2AuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients
    ) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    /**
     * Creates one pooled HTTP transport with bounded connection and response waits.
     */
    @Bean
    ClientHttpRequestFactory partnerRequestFactory(CardDispatchProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }
}
