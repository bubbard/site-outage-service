package com.example.siteoutageservice.service;

import com.example.siteoutageservice.dto.Device;
import com.example.siteoutageservice.dto.DeviceOutage;
import com.example.siteoutageservice.dto.Outage;
import com.example.siteoutageservice.dto.Site;
import com.example.siteoutageservice.web.KrakenWebClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutageDetailServiceTest {

    @Mock
    private KrakenWebClient webClient;

    private final ObjectMapper mapper = new ObjectMapper();

    public final String device1Id = "device1-id";
    public final String device2Id = "device2-id";
    private final String siteId = "some-site-id";
    private Site site;

    private OutageDetailService outageDetailService;
    private Outage outageWayBefore;
    private Outage outageJustBefore;
    private Outage outageOverFilter;
    private Outage outageEqual;
    private Outage outageJustAfter;
    private Outage outageWayAfter;
    private Device device1;
    private Device device2;

    @BeforeEach
    public void setup(){
        outageDetailService = new OutageDetailService(webClient, "2022-01-01T00:00:00.000Z");

        device1 = Device.builder()
                .id(device1Id)
                .name("device1")
                .build();
        device2 = Device.builder()
                .id(device2Id)
                .name("device2")
                .build();

        outageWayBefore = Outage.builder()
                .id(device1Id)
                .begin("2021-01-01T00:00:00.000Z")
                .end("2021-05-01T00:00:00.000Z")
                .build();
        outageJustBefore = Outage.builder()
                .id(device2Id)
                .begin("2021-12-31T23:59:58.999Z")
                .end("2021-12-31T23:59:59.999Z")
                .build();
        outageOverFilter = Outage.builder()
                .id(device1Id)
                .begin("2021-12-31T23:59:59.999Z")
                .end("2022-01-01T00:00:00.001Z")
                .build();
        outageEqual = Outage.builder()
                .id(device2Id)
                .begin("2022-01-01T00:00:00.000Z")
                .end("2022-01-01T00:00:00.001Z")
                .build();
        outageJustAfter = Outage.builder()
                .id(device1Id)
                .begin("2022-01-01T00:00:00.001Z")
                .end("2022-01-01T00:00:01.000Z")
                .build();
        outageWayAfter = Outage.builder()
                .id(device2Id)
                .begin("2022-12-31T00:00:00.000Z")
                .end("2023-01-01T00:00:00.000Z")
                .build();


        site = Site.builder()
                .id(siteId)
                .name("siteName")
                .devices(Arrays.asList(device1, device2))
                .build();
    }

    @Test
    void givenOutagesBeganAfterTimeFilterForSite_whenGettingOutages_thenReturnDeviceOutages(){
        when(webClient.getOutages()).thenReturn(Arrays.asList(outageJustAfter, outageWayAfter));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> outages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(2, outages.size());
        assertEquals(new DeviceOutage(device1, outageJustAfter), outages.get(0));
        assertEquals(new DeviceOutage(device2, outageWayAfter), outages.get(1));
    }

    @Test
    void givenOutagesBeganBeforeTimeFilterForSite_whenGettingOutages_thenReturnEmpty(){
        when(webClient.getOutages()).thenReturn(Arrays.asList(outageJustBefore, outageWayBefore));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> outages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(Collections.emptyList(), outages);
    }

    @Test
    void givenOutagesBeganAtTimeFilterForSite_whenGettingOutages_thenReturnOutagesByDevice(){
        when(webClient.getOutages()).thenReturn(Arrays.asList(outageOverFilter, outageEqual));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> outages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(1, outages.size());
        assertEquals(new DeviceOutage(device2, outageEqual), outages.get(0));
    }

    @Test
    void givenSiteIdDoesNotExist_whenGettingOutages_thenReturnEmptyList(){
        when(webClient.getOutages()).thenReturn(Arrays.asList(outageOverFilter, outageEqual));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.empty());

        List<DeviceOutage> outages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(0, outages.size());
    }

    @Test
    void givenSiteHasNoOutages_whenGettingOutages_thenReturnEmpty(){
        Device device3 = Device.builder()
                .id("abc-123-device3")
                .name("device3")
                .build();

        Site site = Site.builder()
                .id(siteId)
                .name("siteName")
                .devices(Collections.singletonList(device3))
                .build();

        when(webClient.getOutages()).thenReturn(Arrays.asList(outageJustBefore, outageOverFilter, outageEqual, outageJustAfter));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> deviceOutages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(Collections.emptyList(), deviceOutages);
    }

    @Test
    void givenNoOutagesAtAll_whenGettingOutages_thenReturnEmpty(){
        when(webClient.getOutages()).thenReturn(Collections.emptyList());
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> deviceOutages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(Collections.emptyList(), deviceOutages);
    }

    @Test
    void givenBlankSiteId_whenGettingOutages_thenThrowIllegalArgument(){
        assertThrows(IllegalArgumentException.class,
                () -> outageDetailService.getDeviceOutagesFor(" "));

        verifyNoInteractions(webClient);
    }

    @Test
    void givenNullSiteId_whenGettingOutages_thenThrowIllegalArgument(){
        assertThrows(IllegalArgumentException.class,
                () -> outageDetailService.getDeviceOutagesFor(null));

        verifyNoInteractions(webClient);
    }

    @Test
    void givenSiteOutagesBeganBeforeAndAfter_whenGettingOutages_thenReturnOutagesByDeviceAfterFilter() throws IOException {
        List<Outage> outages = mapper.readValue(new File("src/test/resources/outages.json"), new TypeReference<>() {});
        Site siteInfo = mapper.readValue(new File("src/test/resources/site-info-kingfisher.json"), Site.class);
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(siteInfo));
        when(webClient.getOutages()).thenReturn(outages);

        List<DeviceOutage> expectedDeviceOutages = mapper.readValue(new File("src/test/resources/site-outages.json"), new TypeReference<>() {});

        List<DeviceOutage> actualDeviceOutages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(expectedDeviceOutages, actualDeviceOutages);
    }

    @Test
    void givenMultipleDevicesWithSameId_whenGettingOutages_thenShouldUseFirstName(){
        //given - two devices with id=device2Id
        device1 = Device.builder()
                .id(device2Id)
                .name("device1")
                .build();
        device2 = Device.builder()
                .id(device2Id)
                .name("device2")
                .build();

        site = Site.builder()
                .id(siteId)
                .name("siteName")
                .devices(Arrays.asList(device1, device2))
                .build();


        //when - outage associated with id=device2Id
        when(webClient.getOutages()).thenReturn(Collections.singletonList(outageWayAfter));
        when(webClient.getSiteInfoFor(siteId)).thenReturn(Optional.of(site));

        List<DeviceOutage> deviceOutages = outageDetailService.getDeviceOutagesFor(siteId);

        assertEquals(1, deviceOutages.size());
        //then - first device name with given id is used
        assertEquals(device1.name(), deviceOutages.get(0).name());
    }

    @Test
    void givenBlankSiteId_whenPublishingOutages_thenDoNotPublish(){
        List<DeviceOutage> deviceOutages = Arrays.asList(
                new DeviceOutage(device1, outageJustAfter),
                new DeviceOutage(device2, outageWayAfter));

        assertThrows(IllegalArgumentException.class,
                () -> outageDetailService.publishDeviceOutages(" ", deviceOutages));

        verifyNoInteractions(webClient);
    }

    @Test
    void givenNullSiteId_whenPublishingOutages_thenThrowIllegalArgument(){
        List<DeviceOutage> deviceOutages = Arrays.asList(
                new DeviceOutage(device1, outageJustAfter),
                new DeviceOutage(device2, outageWayAfter));

        assertThrows(IllegalArgumentException.class,
                () -> outageDetailService.publishDeviceOutages(null, deviceOutages));

        verifyNoInteractions(webClient);
    }

    @Test
    void givenSiteIdAndDeviceOutages_whenPublishingOutages_thenShouldPublishOutages(){
        List<DeviceOutage> deviceOutages = Arrays.asList(
                new DeviceOutage(device1, outageJustAfter),
                new DeviceOutage(device2, outageWayAfter));

        outageDetailService.publishDeviceOutages(siteId, deviceOutages);

        verify(webClient).postOutagesFor(siteId, deviceOutages);
    }

    @Test
    void givenSiteIdAndEmptyDeviceOutages_whenPublishingOutages_thenShouldPublishOutages(){
        List<DeviceOutage> deviceOutages = Collections.emptyList();

        outageDetailService.publishDeviceOutages(siteId, deviceOutages);

        verify(webClient).postOutagesFor(siteId, deviceOutages);
    }

    @Test
    void givenNullDeviceOutages_whenPublishingOutages_thenThrowIllegalArgument(){
        assertThrows(IllegalArgumentException.class,
                () -> outageDetailService.publishDeviceOutages(siteId, null));

        verifyNoInteractions(webClient);
    }

}