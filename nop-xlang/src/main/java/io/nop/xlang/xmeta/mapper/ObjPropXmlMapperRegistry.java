package io.nop.xlang.xmeta.mapper;

public class ObjPropXmlMapperRegistry {
    static final ObjPropMapperRegistry _instance = new ObjPropMapperRegistry();

    static {
        _instance.registerMapper("xml", new NodeToXmlMapper());
        _instance.registerMapper("json", new NodeToJsonMapper());
    }

    public static ObjPropMapperRegistry instance() {
        return _instance;
    }
}