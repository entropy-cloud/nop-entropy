package io.nop.jq.runtime;

/**
 * Thrown when a jq computation is cancelled (thread interrupt) or exceeds its
 * wall-clock deadline.
 *
 * <p>Deliberately extends {@link RuntimeException} directly, NOT
 * {@link JqRuntimeException}: cancellation is a resource-control decision from
 * the host, never a catchable jq error value. Per the {@link JqHaltException}
 * precedent, jq-level handlers ({@code try}/{@code catch}, {@code ?},
 * {@code ?//}, {@code //} fall-through, {@code recurse} recovery) must not
 * intercept it — otherwise a swallowed cancellation revives the runaway
 * computation the host tried to stop.
 */
public class JqCancellationException extends RuntimeException {
    public JqCancellationException(String message) {
        super(message);
    }

    /** Termination text for consumers at system boundaries (e.g. tool executors). */
    public String errorMessage() {
        return getMessage() == null ? "cancelled" : getMessage();
    }
}
