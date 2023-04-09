package com.example.siteoutageservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceOutage(String id, String name, String begin, String end) {

    public DeviceOutage(Device device, Outage outage){
        this(outage.id(), device.name(), outage.begin(), outage.end());
    }
}
