package io.nop.lint.core.cli;

import java.io.IOException;
import java.io.Writer;

/**
 * One check-report format (roadmap item 39, design 03 §2.4): renders the
 * run's outcome onto a writer. The CLI owns the writer's lifecycle — a
 * reporter writes, never flushes or closes beyond what its format requires.
 *
 * <p>The machine-readable formats ({@code sarif}, {@code checkstyle-xml},
 * {@code json}, {@code junit-xml}) render the diagnostic stream only — no
 * dry-run diff blocks and no suggestion annotations, which are console-only
 * surfaces (plan Decision 1). Line endings are {@code \n} on every format;
 * the console format's golden output is byte-identical to its pre-interface
 * shape.</p>
 */
public interface Reporter {

    /**
     * Renders the outcome (diagnostics plus whatever the format's summary
     * face carries).
     *
     * @throws IOException when the writer fails (the CLI maps that to its
     *                     internal-error exit)
     */
    void render(CheckOutcome outcome, Writer out) throws IOException;
}
