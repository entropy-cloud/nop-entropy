package io.nop.git.ioc;

import io.nop.git.api.IGitRepositoryService;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assembly test (roadmap item 29): the git-ioc.beans.xml file materializes
 * the factory bean with its config-injected properties, and the factory
 * produces a working {@link IGitRepositoryService} (wiring verification:
 * bean definition → container → runtime facade).
 */
class TestGitIocBeans {

    private IBeanContainerImplementor container;

    @BeforeEach
    void setUp() {
        // the xdef schema resolution needs the model registry: initialize the
        // platform core up to (not including) the IoC initializer, like
        // nop-stream-runtime's IoC deployment tests do
        io.nop.core.initialize.CoreInitialization.initializeTo(
                io.nop.core.CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        // the beans file lives on the test classpath (same _vfs layout the
        // platform uses); a FileResource avoids booting the full VFS
        Path beans = Path.of(System.getProperty("user.dir"),
                "src/main/resources/_vfs/nop/git/beans/git-ioc.beans.xml");
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new io.nop.core.resource.impl.FileResource(beans.toAbsolutePath().toFile()));
        container = builder.build("git-ioc-test");
        container.start();
    }

    @AfterEach
    void tearDown() {
        if (container != null)
            container.stop();
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    @Test
    void factoryBeanMaterializesWithConfigProperties() {
        assertTrue(container.containsBean("gitIocFactory"),
                "git-ioc.beans.xml must define the gitIocFactory bean");
        Object bean = container.getBean("gitIocFactory");
        assertTrue(bean instanceof GitIocFactory, "bean must be a GitIocFactory");
        assertEquals("master", ((GitIocFactory) bean).getDefaultBranch(),
                "config property defaultBranch must be injected");
        assertEquals(".nop-git/policy.yaml", ((GitIocFactory) bean).getPolicyPath(),
                "config property policyPath must be injected");
    }

    @Test
    void factoryProducesWorkingRepositoryService(@TempDir Path dir) throws Exception {
        GitIocFactory factory = (GitIocFactory) container.getBean("gitIocFactory");
        IGitRepositoryService svc = factory.initRepository(dir.toFile());
        try {
            Files.writeString(dir.resolve("f.txt"), "x\n");
            svc.add(".");
            svc.commit("first", "t", "t@example.com");
            assertEquals("master", svc.currentBranch());
            assertTrue(svc.status().isClean());
        } finally {
            svc.close();
        }
        assertTrue(Files.exists(dir.resolve(".git")));
        assertNotNull(dir.resolve(".git"));
    }
}
