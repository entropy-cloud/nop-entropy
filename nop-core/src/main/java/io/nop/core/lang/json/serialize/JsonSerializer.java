/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.serialize;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.json.IJsonSerializer;
import io.nop.core.lang.json.IJsonSerializerFactory;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanModelManager;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_JSON_SERIALIZE_ONLY_DATA_BEAN;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ARG_SERIALIZER;
import static io.nop.core.CoreErrors.ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE;
import static io.nop.core.CoreErrors.ERR_JSON_UNKNOWN_SERIALIZER_FOR_PROP;

public class JsonSerializer implements IJsonSerializer {
    static final Logger LOG = LoggerFactory.getLogger(JsonSerializer.class);

    private final IBeanModelManager beanModelManager;
    private final boolean onlyForDataBean;
    private final IJsonSerializerFactory serializerFactory;
    private final IEvalScope scope;

    private final JsonWhitelistChecker checker;

    public JsonSerializer(IBeanModelManager beanModelManager, boolean onlyForDataBean,
                          IJsonSerializerFactory serializerFactory, JsonWhitelistChecker checker, IEvalScope scope) {
        this.beanModelManager = beanModelManager;
        this.onlyForDataBean = onlyForDataBean;
        this.serializerFactory = serializerFactory;
        this.checker = checker;
        this.scope = scope;
    }

    public JsonSerializer(IBeanModelManager beanModelManager, IJsonSerializerFactory serializerFactory,
                          JsonWhitelistChecker checker) {
        this(beanModelManager, CFG_JSON_SERIALIZE_ONLY_DATA_BEAN.get(), serializerFactory, checker,
                DisabledEvalScope.INSTANCE);
    }

    @Override
    public void serializeToJson(SourceLocation loc, Object o, IJsonHandler out) {
        if (o == null) {
            out.rawValue(loc, null);
        } else if (o instanceof String || o instanceof Character) {
            out.stringValue(loc, o.toString());
        } else if (o instanceof Boolean) {
            out.booleanValue(loc, (Boolean) o);
        } else if (o instanceof Number) {
            out.numberValue(loc, (Number) o);
        } else if (o instanceof byte[]) {
            // 字节数组序列化为base64Url，在前台可以直接拼接为dataUrl。例如 data:audio/wav;base64,xxxxxx
            out.stringValue(loc, StringHelper.encodeBase64Url((byte[]) o));
        } else if (o.getClass().isArray()) {
            writeArray(loc, o, out);
        } else if (o instanceof RawText) {
            out.rawValue(loc, o);
        } else if (o instanceof IJsonSerializable) {
            LOG.trace("nop.json.use-json-serializable-interface:o={}", o.getClass());
            ((IJsonSerializable) o).serializeToJson(out);
        } else if (o instanceof Map) {
            writeMap(loc, (Map<String, ?>) o, out);
        } else if (o instanceof Collection) {
            writeCollection(loc, (Collection<?>) o, out);
        } else if (o.getClass().isEnum()) {
            out.stringValue(loc, o.toString());
        } else if (o instanceof IJsonString) {
            out.stringValue(loc, o.toString());
        } else if (o instanceof Class) {
            out.stringValue(loc, ((Class<?>) o).getName());
        } else if (o.getClass() == ByteString.class) {
            out.stringValue(loc, ((ByteString) o).toEncodedBase64());
        } else {
            writeBean(loc, o, out);
        }
    }

    void writeMap(SourceLocation loc, Map<String, ?> map, IJsonHandler out) {
        if (map instanceof ISourceLocationGetter) {
            loc = ((ISourceLocationGetter) map).getLocation();
        }
        out.beginObject(loc);
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            out.key(entry.getKey());
            serializeToJson(loc, entry.getValue(), out);
        }
        out.endObject();
    }

    void writeCollection(SourceLocation loc, Collection<?> coll, IJsonHandler out) {
        if (coll instanceof ISourceLocationGetter)
            loc = ((ISourceLocationGetter) coll).getLocation();

        out.beginArray(loc);
        for (Object value : coll) {
            serializeToJson(loc, value, out);
        }
        out.endArray();
    }

    void writeArray(SourceLocation loc, Object array, IJsonHandler out) {
        out.beginArray(loc);
        for (int i = 0, n = Array.getLength(array); i < n; i++) {
            Object value = Array.get(array, i);
            serializeToJson(loc, value, out);
        }
        out.endArray();
    }

    void writeBean(SourceLocation loc, Object o, IJsonHandler out) {
        Class<?> clazz = o.getClass();
        if (serializerFactory != null) {
            IJsonSerializer serializer = serializerFactory.getSerializerForClass(clazz);
            if (serializer != null) {
                LOG.debug("nop.core.json.use-serializer:className={}", clazz.getName());

                serializer.serializeToJson(loc, o, out);
                return;
            }
        }

        IBeanModel beanModel = beanModelManager.getBeanModelForClass(clazz);
        if (beanModel.getStdDataType().isSimpleType()) {
            out.value(loc, ConvertHelper.toString(o));
            return;
        }

        if (!checker.isAllowSerialize(onlyForDataBean, o, beanModel))
            throw new NopException(ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE).param(ARG_CLASS_NAME, clazz.getName());

        if (beanModel.getSerializer() != null && serializerFactory != null) {
            IJsonSerializer serializer = serializerFactory.getSerializer(beanModel.getSerializer());
            if (serializer != null) {
                LOG.debug("nop.core.json.use-serializer:className={}", clazz.getName());

                serializer.serializeToJson(loc, o, out);
                return;
            }
        }

        // 明确配置的serializer优先于IJsonSerializable接口
        if (o instanceof IJsonSerializable) {
            ((IJsonSerializable) o).serializeToJson(out);
            return;
        }

        out.beginObject(loc);
        for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
            if (!propModel.isSerializable())
                continue;
            Object value = propModel.getPropertyValue(o, scope);
            if (!shouldInclude(propModel, value))
                continue;

            String prop = propModel.getName();
            out.key(prop);
            if (value == null) {
                out.value(loc, null);
            } else {
                if (propModel.getSerializer() != null && serializerFactory != null) {
                    IJsonSerializer serializer = serializerFactory.getSerializer(propModel.getSerializer());
                    if (serializer == null) {
                        throw new NopException(ERR_JSON_UNKNOWN_SERIALIZER_FOR_PROP)
                                .param(ARG_CLASS_NAME, clazz.getName()).param(ARG_PROP_NAME, prop)
                                .param(ARG_SERIALIZER, propModel.getSerializer());
                    }
                    LOG.debug("nop.core.json.use-prop-serializer:className={},propName={}", clazz.getName(), prop);
                    serializer.serializeToJson(loc, value, out);
                } else {
                    serializeToJson(loc, value, out);
                }
            }
        }

        writeExtProps(beanModel, loc, o, out);
        out.endObject();
    }

    private boolean shouldInclude(IBeanPropertyModel propModel, Object value) {
        JsonInclude.Include include = propModel.getJsonInclude();
        if (include != null) {
            if (include == JsonInclude.Include.NON_NULL) {
                if (value == null)
                    return false;
            } else if (include == JsonInclude.Include.NON_EMPTY) {
                if (value == null)
                    return false;

                if (propModel.getType().isCollectionLike()) {
                    if (((Collection<?>) value).isEmpty())
                        return false;
                } else if (propModel.getType().isMapLike()) {
                    if (((Map<?, ?>) value).isEmpty()) {
                        return false;
                    }
                }
            } else if (include == JsonInclude.Include.NON_DEFAULT) {
                if (Objects.equals(value, propModel.getDefaultValue()))
                    return false;
            }
        }

        return true;
    }

    private void writeExtProps(IBeanModel beanModel, SourceLocation loc, Object o, IJsonHandler out) {
        if (beanModel.isAllowGetExtProperty()) {
            Set<String> extPropNames = beanModel.getExtPropertyNames(o);
            if (extPropNames != null && extPropNames.size() > 0) {
                for (String propName : extPropNames) {
                    Object value = beanModel.getExtProperty(o, propName, scope);
                    out.key(propName);
                    serializeToJson(loc, value, out);
                }
            }
        }
    }
}