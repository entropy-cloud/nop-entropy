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
package io.nop.svg.parse;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import static io.nop.svg.SVGErrors.ARG_COL;
import static io.nop.svg.SVGErrors.ARG_CUR;
import static io.nop.svg.SVGErrors.ARG_EXPECTED;
import static io.nop.svg.SVGErrors.ARG_LINE;
import static io.nop.svg.SVGErrors.ERR_SVG_PARSE_FAIL;
import static io.nop.svg.SVGErrors.ERR_SVG_PARSE_UNEXPECTED_CHAR;

/**
 * This class is the superclass of all parsers. It provides localization and error handling methods.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id$
 */
public abstract class AbstractParser implements Parser {

    /**
     * The normalizing reader.
     */
    protected NormalizingReader reader;

    /**
     * The current character.
     */
    protected int current;

    /**
     * Returns the current character value.
     */
    public int getCurrent() {
        return current;
    }

    /**
     * Parses the given reader
     */
    public void parse(Reader r) {
        try {
            reader = new NormalizingReader(r);
            doParse();
        } catch (IOException e) {
            throw error(ERR_SVG_PARSE_FAIL, e);
        }
    }

    protected NopException error(ErrorCode errorCode) {
        return new NopException(errorCode).param(ARG_LINE, reader.getLine()).param(ARG_COL, reader.getColumn());
    }

    protected NopException error(ErrorCode errorCode, Throwable e) {
        return new NopException(errorCode, e).param(ARG_LINE, reader.getLine()).param(ARG_COL, reader.getColumn());
    }

    /**
     * Parses the given input stream. If the encoding is null, ISO-8859-1 is used.
     */
    public void parse(InputStream is, String enc) {

        try {
            parse(IoHelper.toReader(is, enc));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Parses the given string.
     */
    public void parse(String s) {
        parse(new StringReader(s));
    }

    /**
     * Method responsible for actually parsing data after AbstractParser has initialized itself.
     */
    protected abstract void doParse() throws IOException;

    /**
     * simple api to call often reported error. Just a wrapper for reportError().
     *
     * @param currentChar what the caller found and didnt expect
     */
    protected void reportUnexpectedCharacterError(int currentChar) {
        throw error(ERR_SVG_PARSE_UNEXPECTED_CHAR).param(ARG_CUR, (char) currentChar);
    }

    protected void reportCharacterExpectedError(char expected, int currentChar) {
        throw error(ERR_SVG_PARSE_UNEXPECTED_CHAR).param(ARG_EXPECTED, expected).param(ARG_CUR, (char) currentChar);
    }

    /**
     * Skips the whitespaces in the current reader.
     */
    protected void skipSpaces() throws IOException {
        for (; ; ) {
            switch (current) {
                default:
                    return;
                case 0x20:
                case 0x09:
                case 0x0D:
                case 0x0A:
            }
            current = reader.read();
        }
    }

    /**
     * Skips the whitespaces and an optional comma.
     */
    protected void skipCommaSpaces() throws IOException {
        wsp1:
        for (; ; ) {
            switch (current) {
                default:
                    break wsp1;
                case 0x20:
                case 0x9:
                case 0xD:
                case 0xA:
            }
            current = reader.read();
        }
        if (current == ',') {
            wsp2:
            for (; ; ) {
                switch (current = reader.read()) {
                    default:
                        break wsp2;
                    case 0x20:
                    case 0x9:
                    case 0xD:
                    case 0xA:
                }
            }
        }
    }

    /**
     * Parses the content of the buffer and converts it to a float.
     */
    protected float parseFloat() throws IOException {
        int mant = 0;
        int mantDig = 0;
        boolean mantPos = true;
        boolean mantRead = false;

        int exp = 0;
        int expDig = 0;
        int expAdj = 0;
        boolean expPos = true;

        switch (current) {
            case '-':
                mantPos = false;
                // fallthrough
            case '+':
                current = reader.read();
        }

        m1:
        switch (current) {
            default:
                reportUnexpectedCharacterError(current);
                return 0.0f;

            case '.':
                break;

            case '0':
                mantRead = true;
                l:
                for (; ; ) {
                    current = reader.read();
                    switch (current) {
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                            break l;
                        case '.':
                        case 'e':
                        case 'E':
                            break m1;
                        default:
                            return 0.0f;
                        case '0':
                    }
                }

            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                mantRead = true;
                l:
                for (; ; ) {
                    if (mantDig < 9) {
                        mantDig++;
                        mant = mant * 10 + (current - '0');
                    } else {
                        expAdj++;
                    }
                    current = reader.read();
                    switch (current) {
                        default:
                            break l;
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
                    }
                }
        }

        if (current == '.') {
            current = reader.read();
            m2:
            switch (current) {
                default:
                case 'e':
                case 'E':
                    if (!mantRead) {
                        reportUnexpectedCharacterError(current);
                        return 0.0f;
                    }
                    break;

                case '0':
                    if (mantDig == 0) {
                        l:
                        for (; ; ) {
                            current = reader.read();
                            expAdj--;
                            switch (current) {
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                                    break l;
                                default:
                                    if (!mantRead) {
                                        return 0.0f;
                                    }
                                    break m2;
                                case '0':
                            }
                        }
                    }
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    l:
                    for (; ; ) {
                        if (mantDig < 9) {
                            mantDig++;
                            mant = mant * 10 + (current - '0');
                            expAdj--;
                        }
                        current = reader.read();
                        switch (current) {
                            default:
                                break l;
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
                        }
                    }
            }
        }

        switch (current) {
            case 'e':
            case 'E':
                current = reader.read();
                switch (current) {
                    default:
                        reportUnexpectedCharacterError(current);
                        return 0f;
                    case '-':
                        expPos = false;
                    case '+':
                        current = reader.read();
                        switch (current) {
                            default:
                                reportUnexpectedCharacterError(current);
                                return 0f;
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
                        }
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
                }

                en:
                switch (current) {
                    case '0':
                        l:
                        for (; ; ) {
                            current = reader.read();
                            switch (current) {
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                                    break l;
                                default:
                                    break en;
                                case '0':
                            }
                        }

                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        l:
                        for (; ; ) {
                            if (expDig < 3) {
                                expDig++;
                                exp = exp * 10 + (current - '0');
                            }
                            current = reader.read();
                            switch (current) {
                                default:
                                    break l;
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
                            }
                        }
                }
            default:
        }

        if (!expPos) {
            exp = -exp;
        }
        exp += expAdj;
        if (!mantPos) {
            mant = -mant;
        }

        return buildFloat(mant, exp);
    }

    /**
     * Computes a float from mantissa and exponent.
     */
    public static float buildFloat(int mant, int exp) {
        if (exp < -125 || mant == 0) {
            return 0.0f;
        }

        if (exp >= 128) {
            return (mant > 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }

        if (exp == 0) {
            return mant;
        }

        if (mant >= (1 << 26)) {
            mant++; // round up trailing bits if they will be dropped.
        }

        return (float) ((exp > 0) ? mant * pow10[exp] : mant / pow10[-exp]);
    }

    /**
     * Array of powers of ten. Using double instead of float gives a tiny bit more precision.
     */
    private static final double[] pow10 = new double[128];

    static {
        for (int i = 0; i < pow10.length; i++) {
            pow10[i] = Math.pow(10, i);
        }
    }
}
