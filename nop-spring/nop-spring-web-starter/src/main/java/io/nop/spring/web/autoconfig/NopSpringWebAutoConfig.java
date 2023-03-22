package io.nop.spring.web.autoconfig;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.boot.NopApplication;
import io.nop.core.initialize.CoreInitialization;
import io.nop.spring.core.ioc.NopSpringBeanContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import static io.nop.api.core.ApiConfigs.CFG_DEBUG;

@ComponentScan("io.nop.spring")
public class NopSpringWebAutoConfig {

    @Autowired
    ApplicationArguments globalArgs;

    @EventListener
    public void onStart(ApplicationStartedEvent event) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_DEBUG, true);
        NopSpringBeanContainer container = new NopSpringBeanContainer(event.getApplicationContext());
        BeanContainer.registerInstance(container);

        new NopApplication().run(globalArgs.getSourceArgs());
    }

    @EventListener
    public void onClose(ContextClosedEvent event) {
        CoreInitialization.destroy();
    }
}
