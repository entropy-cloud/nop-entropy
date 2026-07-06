package io.nop.core.resource.store;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.CoreConfigs;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTenantResourceStore extends BaseTestCase {
    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setup(){
        setTestConfig(CoreConfigs.CFG_TENANT_RESOURCE_ENABLED,true);
    }

    @AfterEach
    public void tearDown(){
        setTestConfig(CoreConfigs.CFG_TENANT_RESOURCE_ENABLED,false);
    }

    @Test
    public void testTenant(){
        ContextProvider.runWithTenant("2",() ->{
            IResource resource = VirtualFileSystem.instance().getResource("/test.txt");
            assertEquals("abc", resource.readText());
           return null;
        });
    }
}
