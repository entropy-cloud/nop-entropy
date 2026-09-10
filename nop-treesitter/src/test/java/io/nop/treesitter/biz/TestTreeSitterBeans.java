package io.nop.treesitter.biz;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.IResource;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring verification (Minimum Rules #23): both beans resolve from the real
 * {@code app-treesitter.beans.xml} and the container-constructed BizModel —
 * with its provider injected — answers a parse.
 */
class TestTreeSitterBeans {

    private static IBeanContainer container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/treesitter/beans/app-treesitter.beans.xml");
        container = new AppBeanContainerLoader()
                .loadFromResource("test-treesitter-beans", resource);
        container.start();
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    void containerResolvesProviderAndWiredBizModel() {
        TreeSitterBizModel bizModel = (TreeSitterBizModel) container
                .getBean("io.nop.treesitter.biz.TreeSitterBizModel");
        assertEquals("(document\n  (object\n    (pair\n      (string\n        (string_content))\n"
                        + "      (number))))",
                bizModel.parseTreeSitter("{\"a\":1}", "json", (IServiceContext) null));
    }

    @Test
    void unknownLanguageFailsWithDefinedErrorCode() {
        TreeSitterBizModel bizModel = (TreeSitterBizModel) container
                .getBean("io.nop.treesitter.biz.TreeSitterBizModel");
        NopException ex = assertThrows(NopException.class,
                () -> bizModel.parseTreeSitter("[]", "unknown-lang", (IServiceContext) null));
        assertTrue(ex.getMessage().contains("unknown-lang"), ex.getMessage());
    }
}
