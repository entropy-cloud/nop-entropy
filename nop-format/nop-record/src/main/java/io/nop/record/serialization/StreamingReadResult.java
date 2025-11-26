package io.nop.record.serialization;

import io.nop.commons.mutable.MutableValue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

public class StreamingReadResult implements Iterable<Object>, Iterator<Object> {
    private final Object value;
    private final Supplier<Object> nextSupplier;
    private Boolean hasNext;
    private Object nextValue;

    private StreamingReadResult(Object value, Supplier<Object> nextSupplier) {
        this.value = value;
        this.nextSupplier = nextSupplier;
    }

    public boolean isNull(){
        return value == null && nextSupplier == null;
    }

    public static StreamingReadResult ofValue(Object value) {
        return new StreamingReadResult(value, null);
    }

    public static StreamingReadResult ofNext(Supplier<Object> nextSupplier) {
        return new StreamingReadResult(null, nextSupplier);
    }

    public boolean isResolvedValue() {
        return nextSupplier == null;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Iterator<Object> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (hasNext == null) {
            if (value != null) {
                hasNext = true;
                return true;
            }

            if (nextSupplier != null)
                this.nextValue = nextSupplier.get();

            this.hasNext = this.nextValue != null;
        }
        return hasNext;
    }

    @Override
    public Object next() {
        if (!hasNext())
            throw new NoSuchElementException();

        Object result;
        if (value != null) {
            result = value;
            hasNext = false;
        } else {
            result = nextValue;
            // 重置状态，以便下次调用 hasNext() 时重新获取
            hasNext = null;
            nextValue = null;
        }

        return result;
    }

    public StreamingReadResult then(Supplier<StreamingReadResult> action) {
        StreamingReadResult prev = this;
        MutableValue<StreamingReadResult> lazy = new MutableValue<>();
        return ofNext(() -> {
            if (prev.hasNext())
                return prev.next();
            StreamingReadResult next = lazy.lazyGet(action);
            if (next == null)
                return null;
            if (next.hasNext())
                return next.next();
            return null;
        });
    }

    public StreamingReadResult map(Function<Object, Object> fn) {
        if (nextSupplier == null) {
            return ofValue(fn.apply(value));
        }
        return ofNext(() -> fn.apply(nextSupplier.get()));
    }
}