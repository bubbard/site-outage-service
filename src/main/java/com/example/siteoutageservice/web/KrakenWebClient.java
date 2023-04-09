package com.example.siteoutageservice.web;

import com.example.siteoutageservice.dto.DeviceOutage;
import com.example.siteoutageservice.dto.Outage;
import com.example.siteoutageservice.dto.Site;
import com.example.siteoutageservice.exception.KrakenServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.siteoutageservice.config.WebConfiguration.*;

@Component
public class KrakenWebClient {

    private final Logger logger = LoggerFactory.getLogger(KrakenWebClient.class);

    private final WebClient webClient;
    private final long maxRetries;
    private final long minBackoff;

    @Autowired
    public KrakenWebClient(WebClient webClient, @Value("${kraken.rest.maxRetries}") long maxRetries, @Value("${kraken.rest.minBackoff}") long minBackoff){
        this.webClient = webClient;
        this.maxRetries = maxRetries;
        this.minBackoff = minBackoff;
    }

    public List<Outage> getOutages(){
        List<Outage> response = get(Outage[].class, OUTAGES_ENDPOINT)
                .map(Arrays::asList)
                .orElse(Collections.emptyList());

        logger.debug("Response from [{}]: {}", OUTAGES_ENDPOINT, response);

        return response;
    }

    public Optional<Site> getSiteInfoFor(final String siteId){
        final Optional<Site> response = get(Site.class, SITE_INFO_ENDPOINT, siteId);

        logger.debug("Response from [{}]: {}", SITE_INFO_ENDPOINT, response);

        return response;
    }

    public void postOutagesFor(final String siteId, final List<DeviceOutage> detailedOutages){
        WebClient.RequestHeadersSpec<?> spec = webClient.post()
                .uri(SITE_OUTAGES_ENDPOINT, siteId)
                .body(Mono.just(detailedOutages), new ParameterizedTypeReference<>() {});

        call(Void.class, spec, SITE_OUTAGES_ENDPOINT);
    }

    private <T> Optional<T> get(Class<T> responseClass, String endpoint, String... requestFields){
        WebClient.RequestHeadersSpec<?> spec = webClient.get()
                .uri(endpoint, (Object[]) requestFields);

        return call(responseClass, spec, endpoint);
    }

    private <T> Optional<T> call(Class<T> responseClass, WebClient.RequestHeadersSpec<?> spec, String endpoint){
        return spec
                .retrieve()
                .bodyToMono(responseClass)
                .retryWhen(retrySpec(endpoint))
                .blockOptional();
    }

    private RetryBackoffSpec retrySpec(String uri){
        return Retry.backoff(maxRetries, Duration.ofSeconds(minBackoff))
                .filter(KrakenServerException.class::isInstance)
                .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) -> {
                    logger.info("Exhausted {} retry attempts for {}", maxRetries, uri);
                    return retrySignal.failure();
                }));
    }
}
