/**
 * Copyright (c) 2008, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

import java.util.Arrays;

public class YamlHelper {
    public static String escapeYaml(String scalar) {
        if (scalar == null || scalar.length() == 0)
            return scalar;

        // Indicators and special characters.
        boolean blockIndicators = false;
        boolean flowIndicators = false;
        boolean lineBreaks = false;
        boolean specialCharacters = false;

        // Important whitespace combinations.
        boolean leadingSpace = false;
        boolean leadingBreak = false;
        boolean trailingSpace = false;
        boolean trailingBreak = false;
        boolean breakSpace = false;
        boolean spaceBreak = false;

        // Check document indicators.
        if (scalar.startsWith("---") || scalar.startsWith("...")) {
            blockIndicators = true;
            flowIndicators = true;
        }
        // First character or preceded by a whitespace.
        boolean preceededByWhitespace = true;
        boolean followedByWhitespace = scalar.length() == 1 || Constant.NULL_BL_T_LINEBR.has(scalar.codePointAt(1));
        // The previous character is a space.
        boolean previousSpace = false;

        // The previous character is a break.
        boolean previousBreak = false;

        int index = 0;

        while (index < scalar.length()) {
            int c = scalar.codePointAt(index);
            // Check for indicators.
            if (index == 0) {
                // Leading indicators are special characters.
                if ("#,[]{}&*!|>'\"%@`".indexOf(c) != -1) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
                if (c == '?' || c == ':') {
                    flowIndicators = true;
                    if (followedByWhitespace) {
                        blockIndicators = true;
                    }
                }
                if (c == '-' && followedByWhitespace) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
            } else {
                // Some indicators cannot appear within a scalar as well.
                if (",?[]{}".indexOf(c) != -1) {
                    flowIndicators = true;
                }
                if (c == ':') {
                    flowIndicators = true;
                    if (followedByWhitespace) {
                        blockIndicators = true;
                    }
                }
                if (c == '#' && preceededByWhitespace) {
                    flowIndicators = true;
                    blockIndicators = true;
                }
            }
            // Check for line breaks, special, and unicode characters.
            boolean isLineBreak = Constant.LINEBR.has(c);
            if (isLineBreak) {
                lineBreaks = true;
            }
            if (!(c == '\n' || (0x20 <= c && c <= 0x7E))) {
                if (c == 0x85 || (c >= 0xA0 && c <= 0xD7FF)
                        || (c >= 0xE000 && c <= 0xFFFD)
                        || (c >= 0x10000 && c <= 0x10FFFF)) {
                    // unicode is used
                    //if (!this.allowUnicode) {
                    //    specialCharacters = true;
                    //}
                } else {
                    specialCharacters = true;
                }
            }
            // Detect important whitespace combinations.
            if (c == ' ') {
                if (index == 0) {
                    leadingSpace = true;
                }
                if (index == scalar.length() - 1) {
                    trailingSpace = true;
                }
                if (previousBreak) {
                    breakSpace = true;
                }
                previousSpace = true;
                previousBreak = false;
            } else if (isLineBreak) {
                if (index == 0) {
                    leadingBreak = true;
                }
                if (index == scalar.length() - 1) {
                    trailingBreak = true;
                }
                if (previousSpace) {
                    spaceBreak = true;
                }
                previousSpace = false;
                previousBreak = true;
            } else {
                previousSpace = false;
                previousBreak = false;
            }

            // Prepare for the next character.
            index += Character.charCount(c);
            preceededByWhitespace = Constant.NULL_BL_T.has(c) || isLineBreak;
            followedByWhitespace = true;
            if (index + 1 < scalar.length()) {
                int nextIndex = index + Character.charCount(scalar.codePointAt(index));
                if (nextIndex < scalar.length()) {
                    followedByWhitespace = (Constant.NULL_BL_T.has(scalar.codePointAt(nextIndex))) || isLineBreak;
                }
            }
        }
        // Let's decide what styles are allowed.
        boolean allowFlowPlain = true;
        boolean allowBlockPlain = true;
        boolean allowSingleQuoted = true;
        boolean allowBlock = true;
        // Leading and trailing whitespaces are bad for plain scalars.
        if (leadingSpace || leadingBreak || trailingSpace || trailingBreak) {
            allowFlowPlain = allowBlockPlain = false;
        }
        // We do not permit trailing spaces for block scalars.
        if (trailingSpace) {
            allowBlock = false;
        }
        // Spaces at the beginning of a new line are only acceptable for block
        // scalars.
        if (breakSpace) {
            allowFlowPlain = allowBlockPlain = allowSingleQuoted = false;
        }
        // Spaces followed by breaks, as well as special character are only
        // allowed for double quoted scalars.
        if (spaceBreak || specialCharacters) {
            allowFlowPlain = allowBlockPlain = allowSingleQuoted = allowBlock = false;
        }
        // Although the plain scalar writer supports breaks, we never emit
        // multiline plain scalars in the flow context.
        if (lineBreaks) {
            allowFlowPlain = false;
        }
        // Flow indicators are forbidden for flow plain scalars.
        if (flowIndicators) {
            allowFlowPlain = false;
        }
        // Block indicators are forbidden for block plain scalars.
        if (blockIndicators) {
            allowBlockPlain = false;
        }

        if (allowFlowPlain)
            return scalar;

        return StringHelper.quote(scalar);
    }

    static class Constant {
        private final static String ALPHA_S = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_";

        private final static String LINEBR_S = "\n\u0085\u2028\u2029";
        private final static String FULL_LINEBR_S = "\r" + LINEBR_S;
        private final static String NULL_OR_LINEBR_S = "\0" + FULL_LINEBR_S;
        private final static String NULL_BL_LINEBR_S = " " + NULL_OR_LINEBR_S;
        private final static String NULL_BL_T_LINEBR_S = "\t" + NULL_BL_LINEBR_S;
        private final static String NULL_BL_T_S = "\0 \t";
        private final static String URI_CHARS_S = ALPHA_S + "-;/?:@&=+$,_.!~*\'()[]%";

        public final static Constant LINEBR = new Constant(LINEBR_S);
        public final static Constant FULL_LINEBR = new Constant(FULL_LINEBR_S);
        public final static Constant NULL_OR_LINEBR = new Constant(NULL_OR_LINEBR_S);
        public final static Constant NULL_BL_LINEBR = new Constant(NULL_BL_LINEBR_S);
        public final static Constant NULL_BL_T_LINEBR = new Constant(NULL_BL_T_LINEBR_S);
        public final static Constant NULL_BL_T = new Constant(NULL_BL_T_S);
        public final static Constant URI_CHARS = new Constant(URI_CHARS_S);

        public final static Constant ALPHA = new Constant(ALPHA_S);

        private String content;
        boolean[] contains = new boolean[128];
        boolean noASCII = false;

        private Constant(String content) {
            Arrays.fill(contains, false);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                int c = content.codePointAt(i);
                if (c < 128)
                    contains[c] = true;
                else
                    sb.appendCodePoint(c);
            }
            if (sb.length() > 0) {
                noASCII = true;
                this.content = sb.toString();
            }
        }

        public boolean has(int c) {
            return (c < 128) ? contains[c] : noASCII && content.indexOf(c, 0) != -1;
        }

        public boolean hasNo(int c) {
            return !has(c);
        }

        public boolean has(int c, String additional) {
            return has(c) || additional.indexOf(c, 0) != -1;
        }

        public boolean hasNo(int c, String additional) {
            return !has(c, additional);
        }
    }
}
