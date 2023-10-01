package io.nop.file.quarkus.web;

import com.sun.mail.util.LineInputStream;

import java.io.ByteArrayInputStream;

public class QuarkusFix {
    public void load() {
        new LineInputStream(new ByteArrayInputStream(new byte[0]));
    }
}
