package com.example.siteoutageservice.config;

import com.example.siteoutageservice.dto.DeviceOutage;
import com.example.siteoutageservice.exception.KrakenClientException;
import com.example.siteoutageservice.exception.KrakenServerException;
import com.example.siteoutageservice.service.OutageDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ApplicationConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfiguration.class);

    @Bean
    @Autowired
    public ApplicationRunner applicationRunner(OutageDetailService outageDetailService, @Value("${kraken.siteId}") String siteId){
        return args -> {
            logger.debug("Executing with args: [{}]", args.getOptionNames());

            try {
                List<DeviceOutage> outages = outageDetailService.getDeviceOutagesFor(siteId);
                outageDetailService.publishDeviceOutages(siteId, outages);

            } catch (KrakenServerException ex){
                logger.warn("Kraken server failed after multiple retries. Try again later. - {} - {}", ex.getStatus(), ex.getMessage());
            } catch (KrakenClientException ex) {
                logger.error("Client failure when communicating with Kraken - {} - {}", ex.getStatus(), ex.getMessage());
            } catch (IllegalArgumentException ex) {
                logger.error("Misconfigured property - {}", ex.getMessage());
            }
        };
    }
}
