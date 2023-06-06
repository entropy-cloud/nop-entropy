package io.nop.xlang.xmeta.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjPropXmlMapperRegistry {
    private final Map<String, IObjPropMapper> mappers = new ConcurrentHashMap<>();

    static final ObjPropXmlMapperRegistry _instance = new ObjPropXmlMapperRegistry();

    static {
        _instance.registerMapper("xml", new NodeToXmlMapper());
        _instance.registerMapper("json", new NodeToJsonMapper());
    }

    public IObjPropMapper getMapper(String name) {
        return mappers.get(name);
    }

    public void registerMapper(String name, IObjPropMapper mapper) {
        mappers.put(name, mapper);
    }

    public void unregisterMapper(String name, IObjPropMapper mapper) {
        this.mappers.remove(name, mapper);
    }
}