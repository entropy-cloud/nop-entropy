package io.nop.xlang.xmeta.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjPropMapperRegistry {

    private final Map<String, IObjPropMapper> mappers = new ConcurrentHashMap<>();

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
