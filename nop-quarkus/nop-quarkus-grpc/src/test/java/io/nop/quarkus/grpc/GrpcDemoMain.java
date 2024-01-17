package io.nop.quarkus.grpc;

import io.nop.boot.NopApplication;
import io.nop.quarkus.core.QuarkusIntegration;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.event.Observes;

@QuarkusMain
public class GrpcDemoMain {
    static String[] globalArgs;

    public void start(@Observes StartupEvent event) {
        QuarkusIntegration.start();
        new NopApplication().run(globalArgs);
    }
    public static void main(String[] args) {
        globalArgs = args;
        Quarkus.run(args);
    }
}
