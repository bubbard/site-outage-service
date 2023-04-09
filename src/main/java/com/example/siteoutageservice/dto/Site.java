package com.example.siteoutageservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.util.Collection;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record Site(String id, String name, Collection<Device> devices) {
}
