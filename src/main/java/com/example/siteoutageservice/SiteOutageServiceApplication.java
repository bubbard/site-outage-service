package com.example.siteoutageservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SiteOutageServiceApplication {

    private static final Logger logger = LoggerFactory.getLogger(SiteOutageServiceApplication.class);

    public static void main(String[] args) {
        logger.info("STARTING THE APPLICATION");
        SpringApplication.run(SiteOutageServiceApplication.class, args);
        logger.info("APPLICATION FINISHED");
    }
}
