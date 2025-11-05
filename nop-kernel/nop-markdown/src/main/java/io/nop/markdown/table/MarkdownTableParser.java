package io.nop.markdown.table;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.impl.BaseCell;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;

import java.util.function.Predicate;

import static io.nop.core.CoreErrors.ARG_EXPECTED;

public class MarkdownTableParser {

    public static BaseTable parseTable(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        return parseTable(sc);
    }

    public static BaseTable parseTable(TextScanner sc) {
        BaseTable table = new BaseTable();

        // Parse header row
        BaseRow headerRow = parseRow(sc);
        table.addRow(headerRow);

        // Parse separator row (must start with |, -, and end with |)
        validateSeparatorRow(sc);

        // Parse data rows
        while (!sc.isEnd()) {
            if (sc.cur == '|') {
                BaseRow dataRow = parseRow(sc);
                table.addRow(dataRow);
            } else {
                break;
            }
        }

        return table;
    }

    private static BaseRow parseRow(TextScanner sc) {
        BaseRow row = new BaseRow();
        sc.skipBlank();

        // Must start with |
        sc.match('|');

        while (true) {
            sc.skipBlankInLine();

            // Parse cell content using the new helper method
            String cellText = parseMarkdownCellContent(sc);

            BaseCell cell = new BaseCell();
            cell.setValue(cellText);

            // Check for end of cell/row
            if (sc.cur == '|') {
                sc.next();
                row.internalAddCell(cell);
                continue;
            } else if (sc.cur == '\n' || sc.cur == '\r' || sc.isEnd()) {
                sc.skipBlank();
                if (!cellText.isEmpty()) {
                    row.internalAddCell(cell);
                }
                break;
            } else {
                throw sc.newUnexpectedError()
                        .param(ARG_EXPECTED, "| or newline");
            }
        }

        return row;
    }

    /**
     * Parses markdown cell content with proper escaping handling.
     * This method abstracts the complex logic of reading and unescaping markdown text.
     */
    private static String parseMarkdownCellContent(TextScanner sc) {
        MutableString content = new MutableString();
        boolean escaped = false;
        
        while (true) {
            if (escaped) {
                // Handle escaped characters according to CommonMark
                content.append(handleEscapedCharacter(sc));
                escaped = false;
            } else {
                if (sc.cur == '\\') {
                    // Start of escape sequence
                    escaped = true;
                    sc.next();
                } else if (isCellEnd(sc.cur, sc)) {
                    // End of cell
                    break;
                } else {
                    content.append((char)sc.cur);
                    sc.next();
                }
            }
        }

        return content.trim().toString();
    }

    /**
     * Handles a single escaped character according to CommonMark rules.
     */
    private static char handleEscapedCharacter(TextScanner sc) {
        char c = (char)sc.cur;
        sc.next(); // consume the escaped character
        
        switch (c) {
            case '\\': return '\\';
            case '|': return '|';
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            default: return c; // For other escaped characters, just return the character
        }
    }

    /**
     * Checks if the current character marks the end of a cell.
     */
    private static boolean isCellEnd(int currentChar, TextScanner sc) {
        return currentChar == '|' || currentChar == '\n' || currentChar == '\r' || sc.isEnd();
    }

    private static void validateSeparatorRow(TextScanner sc) {
        sc.match('|');

        while (sc.cur != '\n' && !sc.isEnd()) {
            if (sc.cur != '-' && sc.cur != ':' && sc.cur != '|' && !StringHelper.isSpace(sc.cur)) {
                throw sc.newUnexpectedError()
                        .param(ARG_EXPECTED, "only '-', ':', '|' or space in separator row");
            }
            sc.next();
        }
        sc.skipBlank();
    }

    private static Predicate<TextScanner> cellEndPredicate(TextScanner sc) {
        return s -> isCellEnd(s.cur, s);
    }
}