/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.connector;

public class DebeziumConnector {
    public void init(){
//        final Class<Any> keyFormat = (Class<Any>) getFormat(config, PROP_KEY_FORMAT);
//        final Class<Any> valueFormat = (Class<Any>) getFormat(config, PROP_VALUE_FORMAT);
//        final Class<Any> headerFormat = (Class<Any>) getHeaderFormat(config);
//
//        configToProperties(config, props, PROP_SOURCE_PREFIX, "", true);
//        configToProperties(config, props, PROP_FORMAT_PREFIX, "key.converter.", true);
//        configToProperties(config, props, PROP_FORMAT_PREFIX, "value.converter.", true);
//        configToProperties(config, props, PROP_FORMAT_PREFIX, "header.converter.", true);
//        configToProperties(config, props, PROP_KEY_FORMAT_PREFIX, "key.converter.", true);
//        configToProperties(config, props, PROP_VALUE_FORMAT_PREFIX, "value.converter.", true);
//        configToProperties(config, props, PROP_HEADER_FORMAT_PREFIX, "header.converter.", true);
//        configToProperties(config, props, PROP_SINK_PREFIX + name + ".", SchemaHistory.CONFIGURATION_FIELD_PREFIX_STRING + name + ".", false);
//        configToProperties(config, props, PROP_SINK_PREFIX + name + ".", PROP_OFFSET_STORAGE_PREFIX + name + ".", false);
//
//        final Optional<String> transforms = config.getOptionalValue(PROP_TRANSFORMS, String.class);
//        if (transforms.isPresent()) {
//            props.setProperty("transforms", transforms.get());
//            configToProperties(config, props, PROP_TRANSFORMS_PREFIX, "transforms.", true);
//        }
//
//        final Optional<String> predicates = config.getOptionalValue(PROP_PREDICATES, String.class);
//        if (predicates.isPresent()) {
//            props.setProperty("predicates", predicates.get());
//            configToProperties(config, props, PROP_PREDICATES_PREFIX, "predicates.", true);
//        }
//
//        props.setProperty("name", name);
//        LOGGER.debug("Configuration for DebeziumEngine: {}", props);
//
//        engine = DebeziumEngine.create(keyFormat, valueFormat, headerFormat)
//                .using(props)
//                .using((DebeziumEngine.ConnectorCallback) health)
//                .using((DebeziumEngine.CompletionCallback) health)
//                .notifying(consumer)
//                .build();
//
//        executor.execute(() -> {
//            try {
//                engine.run();
//            }
//            finally {
//                Quarkus.asyncExit(returnCode);
//            }
//        });
//        LOGGER.info("Engine executor started");
    }
}
