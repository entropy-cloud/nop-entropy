package io.nop.commons.type;

import io.nop.api.core.annotations.core.Label;

public enum BinaryWordType {
    @Label("byte")
    s1, // Byte

    @Label("unsigned byte")
    u1, // unsigned Byte

    @Label("short little endian")
    s2le, // short little-endian

    @Label("short big endian")
    s2be, // short big endian

    @Label("unsigned short little endian")
    u2le, // unsigned short little-endian

    @Label("unsigned short big endian")
    u2be, // unsigned short big-endian

    @Label("int little endian")
    s4le, // int little-endian

    @Label("int big endian")
    s4be, // int big-endian

    @Label("unsigned int little endian")
    u4le, // unsigned int little-endian

    @Label("unsigned int big endian")
    u4be, // unsigned int big-endian

    @Label("long little endian")
    s8le, // long little-endian

    @Label("long big endian")
    s8be, // long big-endian

    @Label("unsigned long little endian")
    u8le, // unsigned long little-endian

    @Label("unsigned long big endian")
    u8be, // unsigned long big-endian

    @Label("float little endian")
    f4le, // float little-endian

    @Label("float big endian")
    f4be, // float big-endian

    @Label("double little endian")
    f8le, // double little-endian

    @Label("double big endian")
    f8be; // double big-endian

    public String getDefaultValueInitializer() {
        switch (this) {
            case s1:
                return "(byte) 0";
            case u1:
            case s2le:
            case s2be:
                return "(short) 0";
            case u2le:
            case u2be:
            case s4le:
            case s4be:
                return "0";
            case u4le:
            case u4be:
            case s8le:
            case s8be:
            case u8le:
            case u8be:
                return "0L";
            case f4le:
            case f4be:
                return "0.0f";
            case f8le:
            case f8be:
                return "0.0";
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }

    public boolean isLongValue(){
        return getValueType() == Long.class;
    }

    public Class<?> getValueType() {
        switch (this) {
            case s1:
                return Byte.class;
            case u1:
            case s2le:
            case s2be:
                return Short.class;
            case u2le:
            case u2be:
            case s4le:
            case s4be:
                return Integer.class;
            case u4le:
            case u4be:
            case s8le:
            case s8be:
            case u8le:
            case u8be:
                return Long.class;
            case f4le:
            case f4be:
                return Float.class;
            case f8le:
            case f8be:
                return Double.class;
            default:
                throw new IllegalArgumentException("Unknown type: " + this);
        }
    }

    public String getValueTypeName() {
        return getValueType().getSimpleName();
    }
}
