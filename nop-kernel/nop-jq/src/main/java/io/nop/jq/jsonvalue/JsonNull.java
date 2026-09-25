package io.nop.jq.jsonvalue;

public final class JsonNull implements JsonValue {
    static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {
    }

    @Override
    public Object toObject() {
        return null;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public JsonNull asNull() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JsonNull;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "null";
    }
}
