package io.nop.hazelcast.core;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.feature.XModelInclude;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.ByteArrayInputStream;

public class HazelcastProvider {
    private HazelcastInstance hazelcast;
    private String configPath;

    @InjectValue("@cfg:nop.hazelcast.config-path|")
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    @PostConstruct
    public void init() {
        Config config = loadConfig(configPath);
        hazelcast = Hazelcast.newHazelcastInstance(config);
    }

    static Config loadConfig(String path) {
        if(StringHelper.isEmpty(path))
            return null;
        XNode node = XModelInclude.instance().loadActiveNode(path);
        String xml = node.xml();
        ByteArrayInputStream out = new ByteArrayInputStream(xml.getBytes());
        return Config.loadFromStream(out);
    }

    @PreDestroy
    public void destroy() {
        if (hazelcast != null)
            hazelcast.shutdown();
    }

    @BeanMethod
    public HazelcastInstance getInstance() {
        return hazelcast;
    }
}
