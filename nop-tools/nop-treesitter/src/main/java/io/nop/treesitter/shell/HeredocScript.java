package io.nop.treesitter.shell;

import java.util.List;

/**
 * The heredoc body of a script that consists of nothing but that heredoc.
 *
 * <p>The shape is deliberately narrow: exactly one top-level statement, and within
 * it a single heredoc redirect. The reason is that a heredoc's <em>body</em> is
 * only unambiguous when the command that opens it is the only command in the
 * script — given {@code echo x && cat <<EOF} the reader cannot tell whether
 * {@code x} belongs to the heredoc's command or not, and guessing is how a
 * guardrail ends up validating the wrong text.</p>
 *
 * <p>The {@code cd}-prefix form is a second, equally narrow shape: {@code cd dir
 * && <allowed-command> <<EOF}. The separator must be {@code &&}; {@code ;},
 * {@code ||} and {@code |} are not accepted, because they do not guarantee the
 * first command succeeded and therefore do not guarantee the second ran in that
 * directory.</p>
 */
public final class HeredocScript {

    /** Whether the script matched a supported heredoc shape. */
    public sealed interface Lookup permits Found, Absent {

        boolean isFound();

        /** The matched heredoc, or null when the shape did not match. */
        Found found();

        /** Why the shape did not match, or the empty string when it did. */
        String reason();
    }

    /** A matched heredoc. */
    public record Found(String delimiter, String body, boolean quotedDelimiter,
                        String commandName, int bodyStartByte, int bodyEndByte) implements Lookup {

        /**
         * Whether the body is guaranteed literal. A quoted delimiter
         * ({@code '}, {@code "} or {@code \} before it) disables expansion in
         * bash, so such a body is pure text; an unquoted one may contain
         * expansions and is reported as raw source for the caller to treat
         * accordingly.
         */
        public boolean bodyIsLiteral() {
            return quotedDelimiter;
        }

        public List<String> byteRange() {
            return List.of(String.valueOf(bodyStartByte), String.valueOf(bodyEndByte));
        }

        @Override
        public boolean isFound() {
            return true;
        }

        @Override
        public Found found() {
            return this;
        }

        @Override
        public String reason() {
            return "";
        }
    }

    /** No supported heredoc shape; {@code reason} says why. */
    public record Absent(String reason) implements Lookup {

        public static Absent because(String reason) {
            return new Absent(reason);
        }

        @Override
        public boolean isFound() {
            return false;
        }

        @Override
        public Found found() {
            return null;
        }
    }

    private HeredocScript() {
    }
}
