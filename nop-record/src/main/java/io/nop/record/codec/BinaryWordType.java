package io.nop.record.codec;

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
    f8be, // double big-endian
}
