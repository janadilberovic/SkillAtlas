package com.skillatlas.vacayay;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class VacaYayConfig {

    /**
     * Building the client opens no connection, so an unreachable (or simply not started) VacaYAY
     * costs nothing until someone presses Import — same reasoning as the blob container in
     * {@code BlobStorageConfig}: no {@code @SpringBootTest} should need the old system running.
     *
     * <p>The timeouts are the point of the custom factory. Without them a sleeping remote MySQL
     * behind VacaYAY hangs the admin's request until Tomcat gives up on it.
     */
    @Bean
    public RestClient vacaYayRestClient(RestClient.Builder builder,
            @Value("${vacayay.base-url}") String baseUrl,
            @Value("${vacayay.connect-timeout-seconds}") long connectTimeout,
            @Value("${vacayay.read-timeout-seconds}") long readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
