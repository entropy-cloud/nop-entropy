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
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Immutable declaration of one construction parameter accepted by a connector factory
 * (item 19 / P-REQ-28). WI4 (plan 2329) added the optional value-level rules
 * ({@code sensitive} / {@code allowedValues} / {@code pattern}) consumed by
 * conf-validate's connector validation mode; a rule left unset means "no such check"
 * (explicit no-rule semantics, not a silent pass).
 */
public final class ConnectorParamDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final ConnectorParamKind kind;
    private final boolean required;
    private final String description;
    private final boolean sensitive;
    private final List<String> allowedValues;
    private final String pattern;

    public ConnectorParamDescriptor(String name, ConnectorParamKind kind, boolean required, String description) {
        this(name, kind, required, description, false, null, null);
    }

    private ConnectorParamDescriptor(String name, ConnectorParamKind kind, boolean required, String description,
                                     boolean sensitive, List<String> allowedValues, String pattern) {
        if (name == null || name.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor name must not be null or empty");
        }
        if (kind == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor kind must not be null: " + name);
        }
        if (description == null || description.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "param descriptor description must not be null or empty: " + name);
        }
        // Compile eagerly so a malformed rule fails descriptor construction, not the
        // first conf-validate run.
        if (pattern != null) {
            Pattern.compile(pattern);
        }
        if (allowedValues != null && allowedValues.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "allowedValues must be null (no rule) or non-empty: " + name);
        }
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.description = description;
        this.sensitive = sensitive;
        this.allowedValues = allowedValues == null ? null : List.copyOf(allowedValues);
        this.pattern = pattern;
    }

    public static ConnectorParamDescriptor required(String name, ConnectorParamKind kind, String description) {
        return new ConnectorParamDescriptor(name, kind, true, description);
    }

    public static ConnectorParamDescriptor optional(String name, ConnectorParamKind kind, String description) {
        return new ConnectorParamDescriptor(name, kind, false, description);
    }

    /** Required + value rules (WI4). */
    public static ConnectorParamDescriptor required(String name, ConnectorParamKind kind, String description,
                                                    boolean sensitive, List<String> allowedValues, String pattern) {
        return new ConnectorParamDescriptor(name, kind, true, description, sensitive, allowedValues, pattern);
    }

    /** Optional + value rules (WI4). */
    public static ConnectorParamDescriptor optional(String name, ConnectorParamKind kind, String description,
                                                    boolean sensitive, List<String> allowedValues, String pattern) {
        return new ConnectorParamDescriptor(name, kind, false, description, sensitive, allowedValues, pattern);
    }

    /** Read-only view of the allowed-values rule; null = no rule. */
    public List<String> getAllowedValues() {
        return allowedValues == null ? null : Collections.unmodifiableList(allowedValues);
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

    /**
     * Whether the parameter value must never appear in reports, exception params, or
     * sample output (conf-validate renders it as {@code ***}). Null-value credentials
     * held inside OBJECT params are not covered by this flag — it applies to the
     * parameter's own scalar value.
     */
    public boolean isSensitive() {
        return sensitive;
    }

    /** Pattern rule (regex, compiled at construction); null = no rule. */
    public String getPattern() {
        return pattern;
    }

    /** Whether any value rule (allowedValues or pattern) is declared. */
    public boolean hasValueRules() {
        return allowedValues != null || pattern != null;
    }
}
