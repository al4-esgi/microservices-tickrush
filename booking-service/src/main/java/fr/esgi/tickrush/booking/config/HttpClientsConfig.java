package fr.esgi.tickrush.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client HTTP vers payment-service, avec timeouts courts : un appel qui traîne doit
 * échouer vite pour que le circuit breaker puisse compter l'échec (plutôt que de bloquer).
 */
@Configuration
public class HttpClientsConfig {

    @Bean
    RestClient paymentClient(@Value("${payment.base-url}") String baseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(1))
                .withReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
