/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.io.Serializable;

/**
 * Immutable declaration of one construction parameter accepted by a connector factory
 * (item 19 / P-REQ-28).
 */
public final class ConnectorParamDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final ConnectorParamKind kind;
    private final boolean required;
    private final String description;

    public ConnectorParamDescriptor(String name, ConnectorParamKind kind, boolean required, String description) {
        if (name == null || name.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor name must not be null or empty");
        }
        if (kind == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor kind must not be null: " + name);
        }
        if (description == null || description.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor description must not be null or empty: " + name);
        }
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.description = description;
    }

    public static ConnectorParamDescriptor required(String name, ConnectorParamKind kind, String description) {
        return new ConnectorParamDescriptor(name, kind, true, description);
    }

    public static ConnectorParamDescriptor optional(String name, ConnectorParamKind kind, String description) {
        return new ConnectorParamDescriptor(name, kind, false, description);
    }

    /** Parameter name; never null/empty. */
    public String getName() {
        return name;
    }

    /** Value kind (scalar, string-list, or programmatic object). */
    public ConnectorParamKind getKind() {
        return kind;
    }

    /** Whether the factory rejects construction when the parameter is absent. */
    public boolean isRequired() {
        return required;
    }

    /** Short English description of the parameter semantics. */
    public String getDescription() {
        return description;
    }
}
