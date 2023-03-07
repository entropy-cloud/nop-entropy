package io.nop.svg.parse;

/*

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

import java.io.IOException;

import static io.nop.svg.SVGErrors.ARG_CUR;
import static io.nop.svg.SVGErrors.ERR_SVG_PARSE_TRANSFORM_NOT_END;
// tell cpd to start ignoring code - CPD-OFF
/**
 * This class implements an event-based parser for the SVG transform attribute values.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public class SVGTransformParser extends AbstractParser {

    /**
     * The transform list handler used to report parse events.
     */
    protected TransformListHandler transformListHandler;

    /**
     * Creates a new SVGTransformParser.
     */
    public SVGTransformParser(TransformListHandler handler) {
        transformListHandler = handler;
    }

    /**
     * Returns the transform list handler in use.
     */
    public TransformListHandler getTransformListHandler() {
        return transformListHandler;
    }

    /**
     * Parses the current reader.
     */
    protected void doParse() throws IOException {
        transformListHandler.startTransformList();

        loop:
        for (; ; ) {
            current = reader.read();
            switch (current) {
                case 0xD:
                case 0xA:
                case 0x20:
                case 0x9:
                case ',':
                    break;
                case 'm':
                    parseMatrix();
                    break;
                case 'r':
                    parseRotate();
                    break;
                case 't':
                    parseTranslate();
                    break;
                case 's':
                    current = reader.read();
                    switch (current) {
                        case 'c':
                            parseScale();
                            break;
                        case 'k':
                            parseSkew();
                            break;
                        default:
                            reportUnexpectedCharacterError(current);
                            skipTransform();
                    }
                    break;
                case -1:
                    break loop;
                default:
                    reportUnexpectedCharacterError(current);
                    skipTransform();
            }
        }
        skipSpaces();
        if (current != -1) {
            throw error(ERR_SVG_PARSE_TRANSFORM_NOT_END).param(ARG_CUR, (char) current);
        }

        transformListHandler.endTransformList();
    }

    /**
     * Parses a matrix transform. 'm' is assumed to be the current character.
     */
    protected void parseMatrix() throws IOException {
        current = reader.read();

        // Parse 'atrix wsp? ( wsp?'
        if (current != 'a') {
            reportCharacterExpectedError('a', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 't') {
            reportCharacterExpectedError('t', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'r') {
            reportCharacterExpectedError('r', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'i') {
            reportCharacterExpectedError('i', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'x') {
            reportCharacterExpectedError('x', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();
        if (current != '(') {
            reportCharacterExpectedError('(', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        float a = parseFloat();
        skipCommaSpaces();
        float b = parseFloat();
        skipCommaSpaces();
        float c = parseFloat();
        skipCommaSpaces();
        float d = parseFloat();
        skipCommaSpaces();
        float e = parseFloat();
        skipCommaSpaces();
        float f = parseFloat();

        skipSpaces();
        if (current != ')') {
            reportCharacterExpectedError(')', current);
            skipTransform();
            return;
        }

        transformListHandler.matrix(a, b, c, d, e, f);
    }

    /**
     * Parses a rotate transform. 'r' is assumed to be the current character.
     */
    protected void parseRotate() throws IOException {
        current = reader.read();

        // Parse 'otate wsp? ( wsp?'
        if (current != 'o') {
            reportCharacterExpectedError('o', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 't') {
            reportCharacterExpectedError('t', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'a') {
            reportCharacterExpectedError('a', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 't') {
            reportCharacterExpectedError('t', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'e') {
            reportCharacterExpectedError('e', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        if (current != '(') {
            reportCharacterExpectedError('(', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        float theta = parseFloat();
        skipSpaces();

        switch (current) {
            case ')':
                transformListHandler.rotate(theta);
                return;
            case ',':
                current = reader.read();
                skipSpaces();
        }

        float cx = parseFloat();
        skipCommaSpaces();
        float cy = parseFloat();

        skipSpaces();
        if (current != ')') {
            reportCharacterExpectedError(')', current);
            skipTransform();
            return;
        }

        transformListHandler.rotate(theta, cx, cy);
    }

    /**
     * Parses a translate transform. 't' is assumed to be the current character.
     */
    protected void parseTranslate() throws IOException {
        current = reader.read();

        // Parse 'ranslate wsp? ( wsp?'
        if (current != 'r') {
            reportCharacterExpectedError('r', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'a') {
            reportCharacterExpectedError('a', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'n') {
            reportCharacterExpectedError('n', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 's') {
            reportCharacterExpectedError('s', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'l') {
            reportCharacterExpectedError('l', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'a') {
            reportCharacterExpectedError('a', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 't') {
            reportCharacterExpectedError('t', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'e') {
            reportCharacterExpectedError('e', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();
        if (current != '(') {
            reportCharacterExpectedError('(', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        float tx = parseFloat();
        skipSpaces();

        switch (current) {
            case ')':
                transformListHandler.translate(tx);
                return;
            case ',':
                current = reader.read();
                skipSpaces();
        }

        float ty = parseFloat();

        skipSpaces();
        if (current != ')') {
            reportCharacterExpectedError(')', current);
            skipTransform();
            return;
        }

        transformListHandler.translate(tx, ty);
    }

    /**
     * Parses a scale transform. 'c' is assumed to be the current character.
     */
    protected void parseScale() throws IOException {
        current = reader.read();

        // Parse 'ale wsp? ( wsp?'
        if (current != 'a') {
            reportCharacterExpectedError('a', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'l') {
            reportCharacterExpectedError('l', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'e') {
            reportCharacterExpectedError('e', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();
        if (current != '(') {
            reportCharacterExpectedError('(', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        float sx = parseFloat();
        skipSpaces();

        switch (current) {
            case ')':
                transformListHandler.scale(sx);
                return;
            case ',':
                current = reader.read();
                skipSpaces();
        }

        float sy = parseFloat();

        skipSpaces();
        if (current != ')') {
            reportCharacterExpectedError(')', current);
            skipTransform();
            return;
        }

        transformListHandler.scale(sx, sy);
    }

    /**
     * Parses a skew transform. 'e' is assumed to be the current character.
     */
    protected void parseSkew() throws IOException {
        current = reader.read();

        // Parse 'ew[XY] wsp? ( wsp?'
        if (current != 'e') {
            reportCharacterExpectedError('e', current);
            skipTransform();
            return;
        }
        current = reader.read();
        if (current != 'w') {
            reportCharacterExpectedError('w', current);
            skipTransform();
            return;
        }
        current = reader.read();

        boolean skewX = false;
        switch (current) {
            case 'X':
                skewX = true;
                // fall through
            case 'Y':
                break;
            default:
                reportCharacterExpectedError('X', current);
                skipTransform();
                return;
        }
        current = reader.read();
        skipSpaces();
        if (current != '(') {
            reportCharacterExpectedError('(', current);
            skipTransform();
            return;
        }
        current = reader.read();
        skipSpaces();

        float sk = parseFloat();

        skipSpaces();
        if (current != ')') {
            reportCharacterExpectedError(')', current);
            skipTransform();
            return;
        }

        if (skewX) {
            transformListHandler.skewX(sk);
        } else {
            transformListHandler.skewY(sk);
        }
    }

    /**
     * Skips characters in the given reader until a ')' is encountered.
     */
    protected void skipTransform() throws IOException {
        loop:
        for (; ; ) {
            current = reader.read();
            switch (current) {
                case ')':
                    break loop;
                default:
                    if (current == -1) {
                        break loop;
                    }
            }
        }
    }
}
// resume CPD analysis - CPD-ON
