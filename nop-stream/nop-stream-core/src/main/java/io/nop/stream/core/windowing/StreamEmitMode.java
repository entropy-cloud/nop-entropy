/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.exceptions.NopStreamErrors;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * WI37 (D11 release, 2026-10-08): the two-tier window emit mode, locked to the
 * Kafka Streams {@code EmitStrategy} shape — {@link #UPDATE} emits the current
 * window contents on every window update (per record), {@link #CLOSE} keeps the
 * legacy fire-once-at-window-end behavior.
 *
 * <p>Declared surface (F-1, WI37 execution finding): the xdef enum attribute values
 * are the uppercase constant names {@code UPDATE|CLOSE} — the xdef enum dict
 * ({@code EnumStdDomainHandler.parseProp}) matches constant names exactly, the same
 * convention as {@code accumulationMode="DISCARDING"}. The case-insensitive static
 * factory below serves the typed-binding layer (and tolerates the lowercase form
 * recorded by the original D11 note, which it supersedes at the declared-surface
 * level only).
 *
 * <p>Semantics: {@code update} implies {@link AccumulationMode#ACCUMULATING}
 * (contents are kept so per-record emissions show the running aggregate under the
 * D1 last-value-wins terminal-value semantics); {@code close} leaves the declared
 * accumulation mode untouched.
 */
public enum StreamEmitMode {
    @Description("Emit the current window contents on every window update")
    UPDATE,
    @Description("Emit once when the window closes (legacy behavior)")
    CLOSE;

    private static final Map<String, StreamEmitMode> NAME_MAP = Stream.of(values())
            .collect(Collectors.toMap(v -> v.name().toLowerCase(), Function.identity(),
                    (a, b) -> a));

    /**
     * Case-insensitive resolver for the typed-binding layer ({@code EnumTypeConverter}
     * picks this over {@code Enum.valueOf} because of the {@link StaticFactoryMethod}
     * annotation). Accepts the uppercase constant names and their lowercase forms;
     * anything else fails with a typed error (no silent normalization). The xdef parse
     * layer itself validates against the constant names before this is reached.
     */
    @StaticFactoryMethod
    public static StreamEmitMode fromName(String name) {
        if (name == null)
            return null;
        StreamEmitMode mode = NAME_MAP.get(name.toLowerCase());
        if (mode == null) {
            throw new NopException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "emit")
                    .param(NopStreamErrors.ARG_DETAIL,
                            "unknown emit mode (allowed: update|close): " + name);
        }
        return mode;
    }
}
