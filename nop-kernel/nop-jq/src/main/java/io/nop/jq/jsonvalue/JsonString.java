package io.nop.jq.jsonvalue;

public final class JsonString implements JsonValue {
    private final String value;

    JsonString(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public int length() {
        return value.length();
    }

    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public Object toObject() {
        return value;
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public JsonString asString() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JsonString))
            return false;
        return value.equals(((JsonString) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
