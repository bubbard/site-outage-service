package com.example.siteoutageservice.web;

import com.example.siteoutageservice.config.WebConfiguration;
import com.example.siteoutageservice.dto.DeviceOutage;
import com.example.siteoutageservice.dto.ErrorMessage;
import com.example.siteoutageservice.dto.Outage;
import com.example.siteoutageservice.dto.Site;
import com.example.siteoutageservice.exception.KrakenClientException;
import com.example.siteoutageservice.exception.KrakenServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KrakenWebClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    public static MockWebServer mockKraken;

    public KrakenWebClient krakenWebClient;


    @BeforeAll
    public static void setUp() throws IOException {
        mockKraken = new MockWebServer();
        mockKraken.start();
    }

    @BeforeEach
    void initialize() {
        WebConfiguration configuration = new WebConfiguration();
        krakenWebClient = new KrakenWebClient(configuration.webclient("http://localhost:"+ mockKraken.getPort(), "abc-123"),1, 1);
    }

    @AfterAll
    public static void tearDown() throws IOException {
        mockKraken.shutdown();
    }

    @Test
    void given200_whenCallingGetOutages_thenShouldDeserialise() throws JsonProcessingException {
        Outage outage = Outage.builder()
                .id("outage-1")
                .build();
        Outage outage2 = Outage.builder()
                .id("outage-2")
                .build();
        mockKraken.enqueue(new MockResponse()
                        .setResponseCode(200)
                .setBody(mapper.writeValueAsString(Arrays.asList(outage, outage2)))
                .addHeader("Content-Type", "application/json"));

        List<Outage> outageResponse = krakenWebClient.getOutages();

        assertFalse(outageResponse.isEmpty());
        assertEquals(outage, outageResponse.get(0));
        assertEquals(outage2, outageResponse.get(1));
    }

    @Test
    void given200WithEmptyResponse_whenCallingGetOutages_thenShouldDeserialiseToEmptyList() throws JsonProcessingException {
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mapper.writeValueAsString(Collections.emptyList()))
                .addHeader("Content-Type", "application/json"));

        List<Outage> outageResponse = krakenWebClient.getOutages();

        assertTrue(outageResponse.isEmpty());
    }

    @Test
    void given200_whenCallingGetSite_thenShouldDeserialise() throws JsonProcessingException {
        Site site = Site.builder()
                .id("site-id-1")
                        .build();
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mapper.writeValueAsString(site))
                .addHeader("Content-Type", "application/json"));

        Optional<Site> siteResponse = krakenWebClient.getSiteInfoFor("site-id-1");

        assertFalse(siteResponse.isEmpty());
        assertEquals(site, siteResponse.get());
    }

    @Test
    void given200WithEmptyResponse_whenCallingGetSite_thenShouldDeserialiseToEmpty() throws JsonProcessingException {
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mapper.writeValueAsString(null))
                .addHeader("Content-Type", "application/json"));

        Optional<Site> siteResponse = krakenWebClient.getSiteInfoFor("site-id-1");

        assertTrue(siteResponse.isEmpty());
    }


    @Test
    void given5xx_thenShouldRetry() throws JsonProcessingException {
        Outage outage = Outage.builder()
                .id("outage-1")
                .build();
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody(mapper.writeValueAsString(new ErrorMessage("error")))
                .addHeader("Content-Type", "application/json"));
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mapper.writeValueAsString(Collections.singletonList(outage)))
                .addHeader("Content-Type", "application/json"));

        List<Outage> outageResponse = krakenWebClient.getOutages();

        assertFalse(outageResponse.isEmpty());
        assertEquals(outage, outageResponse.get(0));
    }

    @Test
    void given5xxAfterRetriesExhausted_thenShouldThrowServerException() throws JsonProcessingException {
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(502)
                .setBody(mapper.writeValueAsString(new ErrorMessage("error 1")))
                .addHeader("Content-Type", "application/json"));
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody(mapper.writeValueAsString(new ErrorMessage("error 2")))
                .addHeader("Content-Type", "application/json"));

        Exception exception = assertThrows(KrakenServerException.class,
                () -> krakenWebClient.getOutages());

        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains("error 2"));
    }

    @Test
    void given4xx_whenCallingGet_thenShouldThrowClientExceptionAndNotRetry() throws JsonProcessingException {
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody(mapper.writeValueAsString(new ErrorMessage("error 1")))
                .addHeader("Content-Type", "application/json"));

        Exception exception = assertThrows(KrakenClientException.class,
                () -> krakenWebClient.getOutages());

        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains("error 1"));
    }

    @Test
    void given4xx_whenCallingPost_thenShouldThrowClientExceptionAndNotRetry() throws JsonProcessingException {
        DeviceOutage deviceOutage = new DeviceOutage("deviceID", "some-name", "", "");
        List<DeviceOutage> deviceOutages = Collections.singletonList(deviceOutage);
        mockKraken.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody(mapper.writeValueAsString(new ErrorMessage("error 1")))
                .addHeader("Content-Type", "application/json"));

        Exception exception = assertThrows(KrakenClientException.class,
                () -> krakenWebClient.postOutagesFor("siteId", deviceOutages));

        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains("error 1"));
    }

    @Test
    void given200_whenCallingPost_thenShouldNotThrow() {
        DeviceOutage deviceOutage = new DeviceOutage("deviceID", "some-name", "", "");

        mockKraken.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        assertDoesNotThrow(() -> krakenWebClient.postOutagesFor("siteId", Collections.singletonList(deviceOutage)));
    }
}