package demo;

import java.nio.charset.StandardCharsets;

class Clean {
    String decode(byte[] bytes, char[] chars) {
        String fromBytes = new String(bytes, StandardCharsets.UTF_8);
        return fromBytes + new String(chars);
    }
}
