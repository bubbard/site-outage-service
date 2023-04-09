package com.example.siteoutageservice.service;

import com.example.siteoutageservice.dto.Device;
import com.example.siteoutageservice.dto.DeviceOutage;
import com.example.siteoutageservice.dto.Outage;
import com.example.siteoutageservice.dto.Site;
import com.example.siteoutageservice.web.KrakenWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableMap;

@Service
public class OutageDetailService {

    private static final Logger logger = LoggerFactory.getLogger(OutageDetailService.class);
    private final KrakenWebClient krakenWebClient;
    private final ZonedDateTime outagesTimeFilter;

    @Autowired
    public OutageDetailService(KrakenWebClient krakenWebClient, @Value("${kraken.outagesTimeFilter}") String outagesTimeFilter) {
        this.krakenWebClient = krakenWebClient;
        this.outagesTimeFilter = ZonedDateTime.parse(outagesTimeFilter);
    }

    public void publishDeviceOutages(final String siteId, final List<DeviceOutage> detailedOutages){
        Assert.notNull(detailedOutages, "detailedOutages cannot be null.");
        Assert.notNull(siteId, "SiteId cannot be null.");
        Assert.hasLength(siteId.trim(), "SiteId cannot be blank.");

        logger.info("Publishing [{}] detailed outages for siteId [{}]", detailedOutages.size(), siteId);
        logger.debug("Publishing outages: {}", detailedOutages);

        krakenWebClient.postOutagesFor(siteId, detailedOutages);
    }

    public List<DeviceOutage> getDeviceOutagesFor(final String siteId){
        Assert.notNull(siteId, "SiteId cannot be null.");
        Assert.hasLength(siteId.trim(), "SiteId cannot be blank.");

        logger.info("Collecting detailed outages for siteId [{}]", siteId);

        final List<Outage> outages = krakenWebClient.getOutages();

        return krakenWebClient.getSiteInfoFor(siteId)
                .map(Site::devices)
                .map(this::getDeviceById)
                .map(d -> getDetailedOutages(outages, d))
                .orElseGet(Collections::emptyList);
    }

    private Map<String, Device> getDeviceById(final Collection<Device> devices){
        return devices.stream()
                .collect(toUnmodifiableMap(Device::id, Function.identity(), (a,b) -> {
                    logger.warn("Duplicate device Id found for two devices [{}] and [{}]. Continuing with [{}].", a.name(), b.name(), a.name());
                    return a;
                }));
    }

    private List<DeviceOutage> getDetailedOutages(final List<Outage> outages, final Map<String, Device> deviceById){
        return outages.stream()
                .filter(this::isNotBeforeDate)
                .filter(o -> deviceById.containsKey(o.id()))
                .map(o -> new DeviceOutage(deviceById.get(o.id()), o))
                .toList();
    }

    private boolean isNotBeforeDate(Outage o) {
        return !ZonedDateTime.parse(o.begin()).isBefore(outagesTimeFilter);
    }
}
