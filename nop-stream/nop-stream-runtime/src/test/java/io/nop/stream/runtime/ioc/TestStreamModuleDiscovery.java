/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ioc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.IResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the platform IoC discovery convention for the {@code nop/stream} module:
 * the {@code _vfs/nop/stream/_module} marker file makes the module discoverable by
 * {@link ModuleManager}, and a scoped {@link BeanContainerBuilder} that traverses the
 * discovered module's {@code beans/} directory materializes the {@code ioc:default}
 * beans declared there.
 *
 * <p>Unlike {@code TestStreamControlRpcBootstrap} (which hardcodes the beans file path
 * via {@code addResource(new ClassPathResource("...stream-control-rpc.beans.xml"))}),
 * this test discovers the module purely through the {@code _module} marker and traverses
 * its {@code beans/} directory generically — proving the marker genuinely enables the
 * discovery path (plan guide #23 wiring verification). Removing the {@code _module}
 * marker breaks {@code moduleMarkerEnablesDiscovery} (the module would no longer be in
 * the enabled map), so the test carries real bug-catching power.
 *
 * <p>Initialization deliberately stops at {@code INITIALIZER_PRIORITY_IOC - 1} to avoid
 * triggering nop-dao's datasource configuration (see
 * {@code TestStreamControlRpcBootstrap} for the same rationale).
 */
class TestStreamModuleDiscovery {

    private static final String MODULE_NAME = "nop-stream";
    private static final String MODULE_ID = "nop/stream";

    @BeforeAll
    static void init() {
        // VFS + ModuleManager.discover() run at INITIALIZER_PRIORITY_REGISTER_VFS (< IOC),
        // so by this point the _module marker has already driven module discovery.
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void moduleMarkerEnablesDiscovery() {
        // The _vfs/nop/stream/_module marker is what ModuleManager.discover() scans for
        // (VirtualFileSystem.findAll("*/*/_module")). Without it, nop/stream is invisible.
        Map<String, ModuleModel> modules = ModuleManager.instance().getEnabledModuleMap(false);
        assertTrue(modules.containsKey(MODULE_NAME),
                "_vfs/nop/stream/_module marker must make the nop/stream module discoverable");

        ModuleModel streamModule = modules.get(MODULE_NAME);
        assertNotNull(streamModule, "discovered nop/stream module must have a ModuleModel");
        assertTrue(MODULE_ID.equals(streamModule.getModuleId()),
                "discovered module id must be nop/stream, got " + streamModule.getModuleId());
    }

    @Test
    void scopedContainerMaterializesDefaultBeansViaModuleMarker() {
        // _module-driven discovery: obtain the nop/stream module from the discovered map,
        // then traverse its beans/ directory generically (NOT a hardcoded classpath path).
        ModuleModel streamModule = ModuleManager.instance().getEnabledModuleMap(false).get(MODULE_NAME);
        assertNotNull(streamModule, "nop/stream module must be discovered via _module marker");

        List<IResource> beansResources = ModuleManager.instance()
                .findModuleResourcesInModules(Collections.singleton(streamModule), "beans", ".beans.xml");
        assertFalse(beansResources.isEmpty(),
                "_module-driven traversal must find *.beans.xml under _vfs/nop/stream/beans/");

        // The data-plane beans file declares both target ioc:default beans. The control-rpc
        // file intentionally mirrors streamMessageService for control/data-plane co-deployment
        // (resolved by ioc:default in the global container); loading both raw into one scoped
        // container would collide, so we select the data-plane file by name from the discovered
        // set. This still proves the _module -> discovery -> beans/ traversal -> materialization
        // chain (the resource comes from findModuleResourcesInModules, not a hardcoded path).
        IResource dataPlaneBeans = beansResources.stream()
                .filter(r -> r.getName().contains("stream-data-plane"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "discovered beans resources must include stream-data-plane.beans.xml: " + beansResources));

        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(dataPlaneBeans);
        IBeanContainerImplementor container = builder.build("stream-module-discovery-test");
        try {
            container.start();

            // streamMessageService (LocalMessageService) + streamDataPlaneWireCodec
            // (IdentityWireCodec) are ioc:default beans in stream-data-plane.beans.xml.
            assertTrue(container.containsBean("streamMessageService"),
                    "_module-driven discovery must materialize the ioc:default streamMessageService bean");
            assertTrue(container.containsBean("streamDataPlaneWireCodec"),
                    "_module-driven discovery must materialize the ioc:default streamDataPlaneWireCodec bean");

            Object messageService = container.getBean("streamMessageService");
            assertTrue(messageService instanceof IMessageService,
                    "streamMessageService must be an IMessageService, got " + messageService.getClass());
        } finally {
            container.stop();
        }
    }
}
