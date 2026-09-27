package io.nop.stream.core.common.typeutils;

/**
 * A {@link TypeSerializer} that materializes values to/from byte payloads.
 *
 * <p>Provides shared defaults (plan 2278 Phase 2 convergence) for the six
 * boilerplate {@link TypeSerializer} methods both built-in implementations
 * ({@link JavaStreamSerializer}, {@link JsonToolSerializer}) used to
 * hand-write identically: stateful-serializer bookkeeping is unnecessary for
 * these stateless, payload-oriented serializers. Implementations may still
 * override any default when their type semantics demand it.
 *
 * @param <T> the data type that the serializer serializes
 */
public interface IStreamSerializer<T> extends TypeSerializer<T> {

    byte[] serialize(T value);

    T deserialize(byte[] data, Class<T> type);

    @Override
    default boolean isImmutableType() {
        return false;
    }

    @Override
    default TypeSerializer<T> duplicate() {
        return this;
    }

    @Override
    default T createInstance() {
        return null;
    }

    @Override
    default T copy(T from) {
        return from;
    }

    @Override
    default T copy(T from, T reuse) {
        return from;
    }

    /**
     * Variable-length encoding: payload size depends on the value.
     */
    @Override
    default int getLength() {
        return -1;
    }
}
