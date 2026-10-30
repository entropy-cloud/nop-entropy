/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.core.util;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.common.functions.RichFunction;
import io.nop.stream.core.common.functions.StreamFunction;
import io.nop.stream.core.exceptions.StreamException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SERIALIZATION;

/**
 * Plan 2512 C3-009 (adjudication ③, {@code ai-dev/design/concurrency/stream-orchestration-thin-concurrency.md}
 * §3.3): per-subtask privatization of {@code RichFunction} UDFs by Java-serialization
 * round-trip deep copy. A shared RichFunction UDF instance has ONE runtime-context field
 * slot, so parallel subtasks' {@code open()} calls overwrite each other's context and
 * every subtask's keyed state collapses into the last-written backend
 * (last-writer-wins). A private copy per subtask closes that face; non-RichFunction
 * UDFs have no context write face and keep the pass-through reference sharing
 * (stateless-UDF zero regression).
 *
 * <p><b>Shareable-leaf carve-out</b> — the whitelist criteria are transferred verbatim
 * from plan 2511 adjudication ② ({@code ai-dev/design/concurrency/stream-connectors-concurrency-and-envelope.md}
 * §3.2, the single normative source for the ShareRef mechanism): compiled XLang eval
 * functions are immutable executables evaluated against a CALLER-PROVIDED
 * {@code io.nop.core.lang.eval.IEvalScope}; they carry no per-subtask state and
 * implement no RichFunction lifecycle ({@code FunctionUtils} only dispatches
 * {@code setRuntimeContext}/open/close to {@code RichFunction}). Sharing such a leaf
 * by reference reopens NONE of the per-subtask faces. The carve-out is name-scoped to
 * {@code io.nop.xlang.*} classes implementing {@link io.nop.core.lang.eval.IEvalFunction}
 * that are NOT {@link Serializable} and NOT {@link RichFunction}; any OTHER
 * non-Serializable leaf still fails typed — there is no general silent sharing fallback.
 *
 * <p>The module-private round-trip implementations in nop-stream-cep (CepOperator) and
 * nop-stream-connector-debezium (DebeziumSourceSupport / DebeziumCdcSourceFunction)
 * predate this utility and are intentionally left untouched in plan 2512; their
 * migration to this core utility belongs to their owning batches.
 */
@Internal
public final class SerializationRoundTripCopy {

    private SerializationRoundTripCopy() {
    }

    /**
     * Produces the UDF instance handed to a single subtask: {@code RichFunction}
     * instances are round-trip deep copied (per-subtask private runtime-context
     * slot); everything else keeps the shared reference (no context write face,
     * sharing is harmless — plan 2512 adjudication ③ scope exemption).
     *
     * <p>A {@code RichFunction} that is not (transitively) Serializable fails
     * typed here with guidance instead of silently falling back to the shared
     * (unsafe) instance — same deployment-time behavior change as plan 2511
     * adjudication ②, and the same constraint as cross-process deployment
     * serialization.
     *
     * @param function the shared UDF instance; may be null
     * @return the per-subtask instance (a fresh copy for RichFunction inputs)
     */
    public static <F extends StreamFunction> F copyForSubtask(F function) {
        if (function == null) {
            return null;
        }
        if (function instanceof RichFunction) {
            return roundTripCopy(function);
        }
        return function;
    }

    /**
     * Java-serialization round-trip deep copy with the shareable-leaf carve-out
     * described in the class javadoc.
     */
    public static <X> X roundTripCopy(X obj) {
        if (obj == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos) {
                {
                    // The default ObjectOutputStream does NOT enable object
                    // replacement; the shareable-leaf carve-out needs it on.
                    enableReplaceObject(true);
                }

                @Override
                protected Object replaceObject(Object o) {
                    if (isShareableCompiledXlangFunction(o)) {
                        return new ShareRef(o);
                    }
                    return o;
                }
            }) {
                oos.writeObject(obj);
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
                return (X) ois.readObject();
            }
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_SERIALIZATION, e)
                    .param(ARG_DETAIL, "per-subtask copy requires the RichFunction UDF to be Serializable "
                            + "(each subtask needs a private runtime-context slot; only stateless compiled "
                            + "io.nop.xlang.* eval functions are exempted as shareable leaves): "
                            + obj.getClass().getName() + " failed serialization round-trip");
        }
    }

    /**
     * The explicit whitelist of the shareable-leaf carve-out, transferred verbatim
     * from plan 2511 adjudication ② §3.2 (normative source): a compiled XLang eval
     * function — NOT user-serializable, NOT a RichFunction (no udf lifecycle
     * state), and defined by the {@code io.nop.xlang} engine (whose compiled
     * functions are immutable executables over caller-provided scopes).
     * Everything else that is not Serializable keeps failing typed.
     */
    static boolean isShareableCompiledXlangFunction(Object o) {
        return o instanceof io.nop.core.lang.eval.IEvalFunction
                && !(o instanceof Serializable)
                && !(o instanceof RichFunction)
                && o.getClass().getName().startsWith("io.nop.xlang.");
    }

    /**
     * JVM-scoped pool backing the {@link ShareRef} stand-ins; bounded by the number
     * of distinct compiled XLang functions in deployed job graphs (a handful per
     * job) — the shared references are the same immutable compiled objects the rest
     * of the platform reuses anyway.
     */
    private static final ConcurrentHashMap<Integer, Object> SHAREABLE_LEAF_POOL =
            new ConcurrentHashMap<>();

    private static final AtomicInteger SHAREABLE_LEAF_SEQ = new AtomicInteger();

    private static final class ShareRef implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int id;

        ShareRef(Object target) {
            this.id = SHAREABLE_LEAF_SEQ.incrementAndGet();
            SHAREABLE_LEAF_POOL.put(id, target);
        }

        private Object readResolve() {
            Object shared = SHAREABLE_LEAF_POOL.get(id);
            if (shared == null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "shareable XLang function leaf lost from the copy pool: id=" + id);
            }
            return shared;
        }
    }
}
