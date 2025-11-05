/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;

public class GenericFieldSchema {
    private final int propId;

    private final String name;
    private final Label label;

    private final IFieldMarshaller marshaller;

    public GenericFieldSchema(int propId, String name, Label label, IFieldMarshaller marshaller) {
        this.propId = propId;
        this.name = name;
        this.label = label;
        this.marshaller = marshaller;
    }

    public String getName() {
        return name;
    }

    public boolean isObject() {
        return marshaller.isObject();
    }

    public boolean isRequired() {
        return label == Label.LABEL_REQUIRED;
    }

    public boolean isOptional() {
        return label == Label.LABEL_OPTIONAL;
    }

    public boolean isRepeated() {
        return label == Label.LABEL_REPEATED;
    }

    public Label getLabel() {
        return label;
    }

    public int getPropId() {
        return propId;
    }

    public IFieldMarshaller getMarshaller() {
        return marshaller;
    }

    public int computeSize(Object value) {
        return marshaller.computeSize(propId, value);
    }

    public int computeSizeNoTag(Object value) {
        return marshaller.computeSizeNoTag(value);
    }
}
