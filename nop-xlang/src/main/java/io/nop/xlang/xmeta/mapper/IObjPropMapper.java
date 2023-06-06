package io.nop.xlang.xmeta.mapper;

import io.nop.xlang.xmeta.IObjPropMeta;

/**
 * 对象属性在不同场景下进行序列化时可能对应不同的序列化要求
 */
public interface IObjPropMapper {
    Object mapTo(Object obj, IObjPropMeta propMeta, Object value);

    Object mapFrom(Object obj, IObjPropMeta propMeta, Object value);
}