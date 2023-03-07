/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

import io.nop.api.core.util.Guard;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.Charset;

//  copy from UriUtils class of springframework

/**
 * Utility methods for URI encoding and decoding based on RFC 3986.
 *
 * <p>There are two types of encode methods:
 * <ul>
 * <li>{@code "encodeXyz"} -- these encode a specific URI component (e.g. path,
 * query) by percent encoding illegal characters, which includes non-US-ASCII
 * characters, and also characters that are otherwise illegal within the given
 * URI component type, as defined in RFC 3986. The effect of this method, with
 * regards to encoding, is comparable to using the multi-argument constructor
 * of {@link URI}.
 * <li>{@code "encode"} and {@code "encodeUriVariables"} -- these can be used
 * to encode URI variable values by percent encoding all characters that are
 * either illegal, or have any reserved meaning, anywhere within a URI.
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>
 * @since 3.0
 */
public class UriEncodeHelper {

    /**
     * Encode the given source into an encoded String using the rules specified
     * by the given component and with the given options.
     *
     * @param source   the source String
     * @param encoding the encoding of the source String
     * @param type     the URI component for the source
     * @return the encoded URI
     * @throws IllegalArgumentException when the given value is not a valid URI component
     */
    public static String encodeUriComponent(String source, String encoding, Type type) {
        return encodeUriComponent(source, Charset.forName(encoding), type);
    }

    /**
     * Encode the given source into an encoded String using the rules specified
     * by the given component and with the given options.
     *
     * @param source  the source String
     * @param charset the encoding of the source String
     * @param type    the URI component for the source
     * @return the encoded URI
     * @throws IllegalArgumentException when the given value is not a valid URI component
     */
    static String encodeUriComponent(String source, Charset charset, Type type) {
        if (!StringHelper.hasLength(source)) {
            return source;
        }
        Guard.notNull(charset, "Charset must not be null");
        Guard.notNull(type, "Type must not be null");

        byte[] bytes = source.getBytes(charset);
        boolean original = true;
        for (byte b : bytes) {
            if (!type.isAllowed(b)) {
                original = false;
                break;
            }
        }
        if (original) {
            return source;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
        for (byte b : bytes) {
            if (type.isAllowed(b)) {
                baos.write(b);
            } else {
                baos.write('%');
                char hex1 = Character.toLowerCase(Character.forDigit((b >> 4) & 0xF, 16));
                char hex2 = Character.toLowerCase(Character.forDigit(b & 0xF, 16));
                baos.write(hex1);
                baos.write(hex2);
            }
        }
        return new String(baos.toByteArray(), charset);
    }


    /**
     * Enumeration used to identify the allowed characters per URI component.
     * <p>Contains methods to indicate whether a given character is valid in a specific URI component.
     *
     * @see <a href="https://tools.ietf.org/html/rfc3986">RFC 3986</a>
     */
    enum Type {

        SCHEME {
            @Override
            public boolean isAllowed(int c) {
                return isAlpha(c) || isDigit(c) || '+' == c || '-' == c || '.' == c;
            }
        },
        AUTHORITY {
            @Override
            public boolean isAllowed(int c) {
                return isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c;
            }
        },
        USER_INFO {
            @Override
            public boolean isAllowed(int c) {
                return isUnreserved(c) || isSubDelimiter(c) || ':' == c;
            }
        },
        HOST_IPV4 {
            @Override
            public boolean isAllowed(int c) {
                return isUnreserved(c) || isSubDelimiter(c);
            }
        },
        HOST_IPV6 {
            @Override
            public boolean isAllowed(int c) {
                return isUnreserved(c) || isSubDelimiter(c) || '[' == c || ']' == c || ':' == c;
            }
        },
        PORT {
            @Override
            public boolean isAllowed(int c) {
                return isDigit(c);
            }
        },
        PATH {
            @Override
            public boolean isAllowed(int c) {
                return isPchar(c) || '/' == c;
            }
        },
        PATH_SEGMENT {
            @Override
            public boolean isAllowed(int c) {
                return isPchar(c);
            }
        },
        QUERY {
            @Override
            public boolean isAllowed(int c) {
                return isPchar(c) || '/' == c || '?' == c;
            }
        },
        QUERY_PARAM {
            @Override
            public boolean isAllowed(int c) {
                if ('=' == c || '&' == c) {
                    return false;
                } else {
                    return isPchar(c) || '/' == c || '?' == c;
                }
            }
        },
        FRAGMENT {
            @Override
            public boolean isAllowed(int c) {
                return isPchar(c) || '/' == c || '?' == c;
            }
        },
        URI {
            @Override
            public boolean isAllowed(int c) {
                return isUnreserved(c);
            }
        };

        /**
         * Indicates whether the given character is allowed in this URI component.
         *
         * @return {@code true} if the character is allowed; {@code false} otherwise
         */
        public abstract boolean isAllowed(int c);

        /**
         * Indicates whether the given character is in the {@code ALPHA} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isAlpha(int c) {
            return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
        }

        /**
         * Indicates whether the given character is in the {@code DIGIT} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isDigit(int c) {
            return (c >= '0' && c <= '9');
        }

        /**
         * Indicates whether the given character is in the {@code gen-delims} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isGenericDelimiter(int c) {
            return (':' == c || '/' == c || '?' == c || '#' == c || '[' == c || ']' == c || '@' == c);
        }

        /**
         * Indicates whether the given character is in the {@code sub-delims} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isSubDelimiter(int c) {
            return ('!' == c || '$' == c || '&' == c || '\'' == c || '(' == c || ')' == c || '*' == c || '+' == c ||
                    ',' == c || ';' == c || '=' == c);
        }

        /**
         * Indicates whether the given character is in the {@code reserved} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isReserved(int c) {
            return (isGenericDelimiter(c) || isSubDelimiter(c));
        }

        /**
         * Indicates whether the given character is in the {@code unreserved} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isUnreserved(int c) {
            return (isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c);
        }

        /**
         * Indicates whether the given character is in the {@code pchar} set.
         *
         * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
         */
        protected boolean isPchar(int c) {
            return (isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c);
        }
    }
}
