package io.nop.jq.jsonvalue;

public final class JsonBoolean implements JsonValue {
    private final boolean value;

    JsonBoolean(boolean value) {
        this.value = value;
    }

    public boolean value() {
        return value;
    }

    @Override
    public Object toObject() {
        return value;
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public JsonBoolean asBoolean() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JsonBoolean))
            return false;
        return value == ((JsonBoolean) obj).value;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
