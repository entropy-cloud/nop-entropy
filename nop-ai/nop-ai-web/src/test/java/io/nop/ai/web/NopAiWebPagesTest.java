
package io.nop.ai.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.web.page.PageProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class NopAiWebPagesTest extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @Test
    public void testValidateAllPages() {
        List<IResource> pageFiles = new ArrayList<>();
        ModuleManager.instance().getEnabledModules(true).forEach(module -> {
            pageFiles.addAll(VirtualFileSystem.instance().findAll("/" + module.getModuleId(), "pages/*/*.page.yaml"));
        });
        assertFalse(pageFiles.isEmpty(), "expected at least one page definition to validate");

        pageProvider.validateAllPages();

        for (IResource resource : pageFiles) {
            assertNotNull(pageProvider.getPage(resource.getPath(), "zh-CN"),
                    "page should be loadable after validation: " + resource.getPath());
        }
    }
}
