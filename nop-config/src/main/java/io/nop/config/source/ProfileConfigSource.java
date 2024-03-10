/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.commons.util.objects.ValueWithLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 根据profile配置，动态确定实际使用的配置项
 */
public class ProfileConfigSource implements IConfigSource {
    static final Logger LOG = LoggerFactory.getLogger(ProfileConfigSource.class);

    private final List<String> profiles;
    private final IConfigSource source;

    public ProfileConfigSource(List<String> profiles, IConfigSource source) {
        this.profiles = profiles;
        this.source = source;
    }

    @Override
    public String getName() {
        return "profile-conf";
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        Map<String, ValueWithLocation> vars = source.getConfigValues();
        Map<String, ValueWithLocation> ret = new HashMap<>();
        Set<String> keys = new HashSet<>();
        vars.forEach((name, value) -> {
            if (name.charAt(0) == '%') {
                int pos = name.indexOf('.');
                if (pos < 0)
                    return;
                keys.add(name.substring(pos + 1));
                return;
            }
            ret.put(name, value);
        });

        for (String name : keys) {
            for (String profile : profiles) {
                String overrideName = '%' + profile + '.' + name;
                ValueWithLocation vl = vars.get(overrideName);
                if (vl != null) {
                    LOG.debug("nop.config.use-profiled-var:name={},value={},loc={}", overrideName, vl.getValue(),
                            vl.getLocation());
                    ret.put(name, vl);
                    break;
                }
            }
        }
        return ret;
    }

    @Override
    public void addOnChange(Runnable callback) {
        source.addOnChange(callback);
    }

    @Override
    public void close() throws Exception {
        source.close();
    }
}