package io.nop.treesitter.parser.glr;

/**
 * Parser options for the GLR path.
 *
 * @param preferShift when an action group contains both shift and reduce
 *                    actions, take only the shift and drop the reductions.
 *                    Defaults to false — the C runtime explores every action
 *                    of a conflict group (GLR forking), which is what keeps
 *                    corpus output identical to upstream.
 */
public record ParserOptions(boolean preferShift) {

    public static final ParserOptions DEFAULT = new ParserOptions(false);

    public static ParserOptions withPreferShift() {
        return new ParserOptions(true);
    }

    public static ParserOptions withPreferReduce() {
        return new ParserOptions(false);
    }
}