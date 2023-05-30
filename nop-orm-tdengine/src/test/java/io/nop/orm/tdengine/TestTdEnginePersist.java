package io.nop.orm.tdengine;

import org.junit.Rule;
import org.junit.jupiter.api.Disabled;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Disabled
@Testcontainers
public class TestTdEnginePersist {
    @Rule
    public GenericContainer<?> tdengine = new GenericContainer<>(DockerImageName.parse("tdengine/tdengine"))
            .withExposedPorts(5001);

}
