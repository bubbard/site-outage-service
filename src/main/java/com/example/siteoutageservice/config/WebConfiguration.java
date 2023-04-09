package com.example.siteoutageservice.config;

import com.example.siteoutageservice.dto.ErrorMessage;
import com.example.siteoutageservice.exception.KrakenClientException;
import com.example.siteoutageservice.exception.KrakenServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WebConfiguration.class);

    public static final String OUTAGES_ENDPOINT = "/outages";
    public static final String SITE_INFO_ENDPOINT = "/site-info/{siteId}";
    public static final String SITE_OUTAGES_ENDPOINT = "/site-outages/{siteId}";

    @Bean
    public WebClient webclient(@Value("${kraken.rest.baseUrl}") String baseUrl, @Value("${kraken.rest.apiKey}") String apiKey) {
        logger.debug("Loaded Kraken base url [{}]", baseUrl);
        return WebClient
                .builder()
                .filter(requestLogger())
                .filter(errorHandler())
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    public ExchangeFilterFunction requestLogger() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }

    public ExchangeFilterFunction errorHandler() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            logger.info("Completed with status: {}", clientResponse.statusCode().value());

            if (clientResponse.statusCode().is5xxServerError()) {
                return clientResponse.bodyToMono(ErrorMessage.class)
                        .flatMap(errorBody -> Mono.error(new KrakenServerException(errorBody.message(), clientResponse.statusCode().value())));

            } else if (clientResponse.statusCode().is4xxClientError()) {
                return clientResponse.bodyToMono(ErrorMessage.class)
                        .flatMap(errorBody -> Mono.error(new KrakenClientException(errorBody.message(), clientResponse.statusCode().value())));

            } else {
                return Mono.just(clientResponse);
            }
        });
    }
}
