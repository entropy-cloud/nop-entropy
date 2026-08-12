/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.totp;

import io.nop.commons.util.StringHelper;

/**
 * Minimal RFC 4648 Base32 (A-Z2-7) encoder/decoder for TOTP secrets.
 * No third-party dependency: the platform has no Base32 utility and the design
 * (§3.4) explicitly rejected pulling in a TOTP/OTP library for ~50 lines of code.
 */
final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE_TABLE = new int[128];

    static {
        for (int i = 0; i < DECODE_TABLE.length; i++) {
            DECODE_TABLE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE_TABLE[ALPHABET.charAt(i)] = i;
            // accept lowercase as well
            DECODE_TABLE[Character.toLowerCase(ALPHABET.charAt(i))] = i;
        }
    }

    private Base32() {
    }

    static String encode(byte[] data) {
        if (data == null || data.length == 0)
            return "";
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(ALPHABET.charAt(idx));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(ALPHABET.charAt(idx));
        }
        // pad to multiple of 8
        while (sb.length() % 8 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    static byte[] decode(String str) {
        if (StringHelper.isEmpty(str))
            return new byte[0];
        // strip whitespace and padding
        String clean = str.replace("=", "").replace(" ", "").trim();
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int idx = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] < 0) {
                throw new IllegalArgumentException("invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | DECODE_TABLE[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
                if (idx >= out.length)
                    break;
            }
        }
        return out;
    }
}
