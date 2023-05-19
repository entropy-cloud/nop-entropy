package io.nop.cluster.chooser;

import com.vdurmont.semver4j.Requirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRouteFilter {
    @Test
    public void testVersion(){
        String ver = "^2.0";
        Requirement requirement = Requirement.buildNPM(ver);
        assertTrue(requirement.isSatisfiedBy("2.2.0"));
    }
}
