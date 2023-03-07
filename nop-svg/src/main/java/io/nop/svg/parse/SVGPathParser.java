/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.svg.parse;

import io.nop.svg.model.SVGPath;

import java.io.IOException;
import java.io.Reader;

import static io.nop.svg.SVGErrors.ERR_SVG_PARSE_PATH_NOT_END;

/**
 * This class implements an event-based parser for the SVG path's d attribute values.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id: SVGPathParser.java 502167 2007-02-01 09:26:51Z dvholten $
 */
public class SVGPathParser extends AbstractParser {
// tell cpd to start ignoring code - CPD-OFF
    /**
     * The path handler used to report parse events.
     */
    protected AWTPathProducer pathHandler;
    protected Reader reader;

    /**
     * Creates a new SVGPathParser.
     */
    public SVGPathParser() {
        pathHandler = new AWTPathProducer();
    }

    public void setPathHandler(AWTPathProducer handler) {
        pathHandler = handler;
    }

    public SVGPath getSVGPath() {
        return pathHandler.path;
    }

    protected void doParse() throws IOException {
        pathHandler.startPath();

        current = reader.read();
        loop:
        for (; ; ) {
            switch (current) {
                case 0xD:
                case 0xA:
                case 0x20:
                case 0x9:
                    current = reader.read();
                    break;
                case 'z':
                case 'Z':
                    current = reader.read();
                    pathHandler.closePath();
                    break;
                case 'm':
                    parsem();
                    break;
                case 'M':
                    parseM();
                    break;
                case 'l':
                    parsel();
                    break;
                case 'L':
                    parseL();
                    break;
                case 'h':
                    parseh();
                    break;
                case 'H':
                    parseH();
                    break;
                case 'v':
                    parsev();
                    break;
                case 'V':
                    parseV();
                    break;
                case 'c':
                    parsec();
                    break;
                case 'C':
                    parseC();
                    break;
                case 'q':
                    parseq();
                    break;
                case 'Q':
                    parseQ();
                    break;
                case 's':
                    parses();
                    break;
                case 'S':
                    parseS();
                    break;
                case 't':
                    parset();
                    break;
                case 'T':
                    parseT();
                    break;
                case 'a':
                    parsea();
                    break;
                case 'A':
                    parseA();
                    break;
                case -1:
                    break loop;
                default:
                    reportUnexpected(current);
                    break;
            }

        }

        skipSpaces();
        if (current != -1) {
            throw error(ERR_SVG_PARSE_PATH_NOT_END);
        }

        pathHandler.endPath();
    }

    /**
     * Parses a 'm' command.
     */
    protected void parsem() throws IOException {
        current = reader.read();
        skipSpaces();

        float x = parseFloat();
        skipCommaSpaces();
        float y = parseFloat();
        pathHandler.movetoRel(x, y);

        boolean expectNumber = skipCommaSpaces2();
        _parsel(expectNumber);
    }

    /**
     * Parses a 'M' command.
     */
    protected void parseM() throws IOException {
        current = reader.read();
        skipSpaces();

        float x = parseFloat();
        skipCommaSpaces();
        float y = parseFloat();
        pathHandler.movetoAbs(x, y);

        boolean expectNumber = skipCommaSpaces2();
        _parseL(expectNumber);
    }

    /**
     * Parses a 'l' command.
     */
    protected void parsel() throws IOException {
        current = reader.read();
        skipSpaces();
        _parsel(true);
    }

    protected void _parsel(boolean expectNumber) throws IOException {
        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;
                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.linetoRel(x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'L' command.
     */
    protected void parseL() throws IOException {
        current = reader.read();
        skipSpaces();
        _parseL(true);
    }

    protected void _parseL(boolean expectNumber) throws IOException {
        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;
                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.linetoAbs(x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'h' command.
     */
    protected void parseh() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;
                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();
            pathHandler.linetoHorizontalRel(x);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'H' command.
     */
    protected void parseH() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();
            pathHandler.linetoHorizontalAbs(x);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'v' command.
     */
    protected void parsev() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();

            pathHandler.linetoVerticalRel(x);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'V' command.
     */
    protected void parseV() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }
            float x = parseFloat();

            pathHandler.linetoVerticalAbs(x);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'c' command.
     */
    protected void parsec() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x1 = parseFloat();
            skipCommaSpaces();
            float y1 = parseFloat();
            skipCommaSpaces();
            float x2 = parseFloat();
            skipCommaSpaces();
            float y2 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoCubicRel(x1, y1, x2, y2, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'C' command.
     */
    protected void parseC() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x1 = parseFloat();
            skipCommaSpaces();
            float y1 = parseFloat();
            skipCommaSpaces();
            float x2 = parseFloat();
            skipCommaSpaces();
            float y2 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoCubicAbs(x1, y1, x2, y2, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'q' command.
     */
    protected void parseq() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x1 = parseFloat();
            skipCommaSpaces();
            float y1 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoQuadraticRel(x1, y1, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'Q' command.
     */
    protected void parseQ() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x1 = parseFloat();
            skipCommaSpaces();
            float y1 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoQuadraticAbs(x1, y1, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 's' command.
     */
    protected void parses() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x2 = parseFloat();
            skipCommaSpaces();
            float y2 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoCubicSmoothRel(x2, y2, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'S' command.
     */
    protected void parseS() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x2 = parseFloat();
            skipCommaSpaces();
            float y2 = parseFloat();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoCubicSmoothAbs(x2, y2, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 't' command.
     */
    protected void parset() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoQuadraticSmoothRel(x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'T' command.
     */
    protected void parseT() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.curvetoQuadraticSmoothAbs(x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'a' command.
     */
    protected void parsea() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float rx = parseFloat();
            skipCommaSpaces();
            float ry = parseFloat();
            skipCommaSpaces();
            float ax = parseFloat();
            skipCommaSpaces();

            boolean laf;
            switch (current) {
                default:
                    reportUnexpected(current);
                    return;
                case '0':
                    laf = false;
                    break;
                case '1':
                    laf = true;
                    break;
            }

            current = reader.read();
            skipCommaSpaces();

            boolean sf;
            switch (current) {
                default:
                    reportUnexpected(current);
                    return;
                case '0':
                    sf = false;
                    break;
                case '1':
                    sf = true;
                    break;
            }

            current = reader.read();
            skipCommaSpaces();

            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.arcRel(rx, ry, ax, laf, sf, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Parses a 'A' command.
     */
    protected void parseA() throws IOException {
        current = reader.read();
        skipSpaces();
        boolean expectNumber = true;

        for (; ; ) {
            switch (current) {
                default:
                    if (expectNumber)
                        reportUnexpected(current);
                    return;

                case '+':
                case '-':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
            }

            float rx = parseFloat();
            skipCommaSpaces();
            float ry = parseFloat();
            skipCommaSpaces();
            float ax = parseFloat();
            skipCommaSpaces();

            boolean laf;
            switch (current) {
                default:
                    reportUnexpected(current);
                    return;
                case '0':
                    laf = false;
                    break;
                case '1':
                    laf = true;
                    break;
            }

            current = reader.read();
            skipCommaSpaces();

            boolean sf;
            switch (current) {
                default:
                    reportUnexpected(current);
                    return;
                case '0':
                    sf = false;
                    break;
                case '1':
                    sf = true;
                    break;
            }

            current = reader.read();
            skipCommaSpaces();
            float x = parseFloat();
            skipCommaSpaces();
            float y = parseFloat();

            pathHandler.arcAbs(rx, ry, ax, laf, sf, x, y);
            expectNumber = skipCommaSpaces2();
        }
    }

    /**
     * Skips a sub-path.
     */
    protected void skipSubPath() throws IOException {
        for (; ; ) {
            switch (current) {
                case -1:
                case 'm':
                case 'M':
                    return;
                default:
                    break;
            }
            current = reader.read();
        }
    }

    protected void reportUnexpected(int ch) throws IOException {
        this.reportUnexpectedCharacterError(ch);
    }

    /**
     * Skips the whitespaces and an optional comma.
     *
     * @return true if comma was skipped.
     */
    protected boolean skipCommaSpaces2() throws IOException {
        wsp1:
        for (; ; ) {
            switch (current) {
                default:
                    break wsp1;
                case 0x20:
                case 0x9:
                case 0xD:
                case 0xA:
                    break;
            }
            current = reader.read();
        }

        if (current != ',')
            return false; // no comma.

        wsp2:
        for (; ; ) {
            switch (current = reader.read()) {
                default:
                    break wsp2;
                case 0x20:
                case 0x9:
                case 0xD:
                case 0xA:
                    break;
            }
        }
        return true; // had comma
    }
// resume CPD analysis - CPD-ON
}
