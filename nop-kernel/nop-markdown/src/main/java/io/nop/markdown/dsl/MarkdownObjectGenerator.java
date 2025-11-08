package io.nop.markdown.dsl;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;

public class MarkdownObjectGenerator {

    public String generateText() {
        StringBuilder sb = new StringBuilder();
        try {
            generate(sb);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return sb.toString();
    }

    public void generate(Appendable out) throws IOException {

    }
}
