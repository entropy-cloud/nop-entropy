package io.nop.core.resource.tenant;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.store.DeltaResourceStore;
import io.nop.core.resource.store.IDeltaResourceStore;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTenantResourceStore extends BaseTestCase {
    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();;
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @Test
    public void testTenantSuper(){
        DeltaResourceStore deltaStore = new DeltaResourceStore();
        InMemoryResourceStore store = new InMemoryResourceStore();
        store.addResource(new InMemoryTextResource("/nop/test.txt","aaa"));
        deltaStore.setStore(store);
        IResource resource = deltaStore.getSuperResource("/_tenant/1/nop/test.txt",false);
        assertEquals("aaa",resource.readText());
    }
}
