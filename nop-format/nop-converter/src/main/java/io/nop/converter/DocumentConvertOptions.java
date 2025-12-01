package io.nop.converter;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.IResourceDslNodeLoader;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class DocumentConvertOptions {
    private boolean allowChained;
    private IResourceDslNodeLoader.ResolvePhase dslNodeResolvePhase;
    private Map<String, Object> properties;

    public static DocumentConvertOptions create() {
        return new DocumentConvertOptions();
    }

    public DocumentConvertOptions allowChained() {
        this.allowChained = true;
        return this;
    }

    public DocumentConvertOptions dslNodeResolvePhase(IResourceDslNodeLoader.ResolvePhase phase) {
        this.dslNodeResolvePhase = phase;
        return this;
    }

    public IResourceDslNodeLoader.ResolvePhase getDslNodeResolvePhase() {
        return dslNodeResolvePhase;
    }

    public void setDslNodeResolvePhase(IResourceDslNodeLoader.ResolvePhase dslNodeResolvePhase) {
        this.dslNodeResolvePhase = dslNodeResolvePhase;
    }

    public boolean isAllowChained() {
        return allowChained;
    }

    public void setAllowChained(boolean allowChained) {
        this.allowChained = allowChained;
    }

    public Object getProperty(String name) {
        return properties == null ? null : properties.get(name);
    }

    public void setProperty(String name, Object value) {
        if (properties == null)
            properties = new LinkedHashMap<>();
        properties.put(name, value);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}