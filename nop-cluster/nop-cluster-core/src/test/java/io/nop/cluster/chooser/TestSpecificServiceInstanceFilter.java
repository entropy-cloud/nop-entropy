package io.nop.cluster.chooser;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.cluster.chooser.filter.SpecificServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestSpecificServiceInstanceFilter {

    private final SpecificServiceInstanceFilter filter = new SpecificServiceInstanceFilter();

    private ServiceInstance instance(String addr, int port) {
        ServiceInstance inst = new ServiceInstance();
        inst.setAddr(addr);
        inst.setPort(port);
        return inst;
    }

    @Test
    public void testFilterByHeaderTargetHost() {
        filter.setEnabled(true);

        List<ServiceInstance> instances = new ArrayList<>(List.of(
                instance("10.0.0.1", 8080),
                instance("10.0.0.2", 8080),
                instance("10.0.0.3", 8080)
        ));

        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setSvcTargetHost(request, "10.0.0.2:8080");

        filter.filter(instances, request, false);

        assertEquals(1, instances.size());
        assertEquals("10.0.0.2", instances.get(0).getAddr());
    }

    @Test
    public void testNoHeaderReturnsAll() {
        filter.setEnabled(true);

        List<ServiceInstance> instances = new ArrayList<>(List.of(
                instance("10.0.0.1", 8080),
                instance("10.0.0.2", 8080)
        ));

        ApiRequest<?> request = new ApiRequest<>();
        filter.filter(instances, request, false);

        assertEquals(2, instances.size());
    }

    @Test
    public void testHeaderNotMatchingRemovesAll() {
        filter.setEnabled(true);

        List<ServiceInstance> instances = new ArrayList<>(List.of(
                instance("10.0.0.1", 8080),
                instance("10.0.0.2", 8080)
        ));

        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setSvcTargetHost(request, "10.0.0.9:8080");

        filter.filter(instances, request, false);

        assertEquals(0, instances.size());
    }

    @Test
    public void testDisabledFilterDoesNothing() {
        filter.setEnabled(false);

        List<ServiceInstance> instances = new ArrayList<>(List.of(
                instance("10.0.0.1", 8080),
                instance("10.0.0.2", 8080)
        ));

        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setSvcTargetHost(request, "10.0.0.1:8080");

        // isEnabled=false → chooser should skip this filter entirely,
        // but calling filter() directly still works (it doesn't check enabled)
        filter.filter(instances, request, false);
        assertEquals(1, instances.size());
    }
}
