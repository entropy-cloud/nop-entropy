package io.nop.jq.jsonvalue;

public final class JsonNumber implements JsonValue {
    private final Number value;

    JsonNumber(Number value) {
        this.value = value;
    }

    public Number value() {
        return value;
    }

    public int intValue() {
        return value.intValue();
    }

    public long longValue() {
        return value.longValue();
    }

    public double doubleValue() {
        return value.doubleValue();
    }

    @Override
    public Object toObject() {
        return value;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    public JsonNumber asNumber() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JsonNumber))
            return false;
        JsonNumber other = (JsonNumber) obj;
        if (value instanceof Integer && other.value instanceof Integer)
            return value.intValue() == other.value.intValue();
        if (value instanceof Long && other.value instanceof Long)
            return value.longValue() == other.value.longValue();
        return Double.compare(value.doubleValue(), other.value.doubleValue()) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value.doubleValue());
    }

    @Override
    public String toString() {
        if (value instanceof Double) {
            double d = value.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d))
                return Long.toString((long) d);
        }
        return value.toString();
    }
}
