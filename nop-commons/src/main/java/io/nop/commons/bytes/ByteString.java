/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.bytes;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.convert.IByteArrayView;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.CommonConstants;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.ByteHelper;
import io.nop.commons.util.StringHelper;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import static io.nop.api.core.ApiErrors.ARG_SRC_TYPE;
import static io.nop.commons.CommonErrors.ERR_BYTES_CONVERT_TO_BYTE_STRING_FAIL;

// 从okio拷贝了部分实现代码

/**
 * 标记为final，不允许有派生类。类似于String类对字节数组进行了只读封装，不对外暴露内部数组结构，toByteArray返回的是拷贝生成的新数组。
 *
 * @author canonical_entropy@163.com
 */
@ImmutableBean
public final class ByteString implements Serializable, Comparable<ByteString>, IByteArrayView {

    private static final long serialVersionUID = -1607482161494270718L;
    /**
     * A singleton empty {@code ByteString}.
     */
    public static final ByteString EMPTY = new ByteString(EMPTY_BYTES);

    private final byte[] bytes;
    private transient int hashCode; // Lazily computed; 0 if unknown.
    private transient String utf8; // Lazily computed.

    private ByteString(byte[] bytes) {
        this.bytes = bytes == null ? EMPTY_BYTES : bytes;
    }

    private ByteString(String str) {
        this(str.getBytes(StandardCharsets.UTF_8));
    }

    public static ByteString from(Object value, Function<ErrorCode, NopException> errorFactory) {
        if (value == null)
            return null;
        if (value instanceof ByteString)
            return (ByteString) value;

        if (value instanceof byte[])
            return new ByteString((byte[]) value);

        if (value instanceof ByteBuffer)
            return new ByteString(ByteBufferHelper.getBytes((ByteBuffer) value));

        throw errorFactory.apply(ERR_BYTES_CONVERT_TO_BYTE_STRING_FAIL).param(ARG_SRC_TYPE, value.getClass());
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public int length() {
        return bytes.length;
    }

    public byte at(int index) {
        return bytes[index];
    }

    @Override
    public int compareTo(ByteString object) {
        byte[] bs = object.bytes;
        if (bytes == bs)
            return 0;

        return ByteHelper.compareTo(bytes, bs);
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        return result != 0 ? result : (hashCode = Arrays.hashCode(bytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof ByteString))
            return false;
        return Arrays.equals(bytes, ((ByteString) o).bytes);
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    public ByteBuffer appendTo(ByteBuffer buf) {
        buf.put(bytes);
        return buf;
    }

    @Override
    public byte[] toByteArray() {
        return bytes;
    }

    @Override
    public InputStream toInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    public String hex() {
        if (bytes.length <= 0)
            return "";
        return StringHelper.bytesToHex(bytes);
    }

    public String toString() {
        return "ByteString[size=" + bytes.length + "]";
    }

    public String toEncodedBase64() {
        return CommonConstants.BASE_64_PREFIX + base64Url();
    }

    public String toEncodedHex() {
        return CommonConstants.HEX_PREFIX + hex();
    }

    public boolean isSafeUtf8() {
        for (int i = 0, n = bytes.length; i < n; i++) {
            byte b = bytes[i];
            if (!StringHelper.isDigit(b) || !StringHelper.isAsciiLetter(b) || b == '-' || b == '_') {
                return false;
            }
        }
        return true;
    }

    public String toStringBinary() {
        if (bytes == null)
            return null;
        else
            return ByteHelper.toStringBinary(bytes, 0, bytes.length);
    }

    public String utf8() {
        String result = utf8;
        // We don't care if we double-allocate in racy code.
        return result != null ? result : (utf8 = new String(bytes, StringHelper.CHARSET_UTF8));
    }

    public String toString(String encoding) {
        if (encoding == null || encoding.equalsIgnoreCase(StringHelper.ENCODING_UTF8))
            return utf8();

        try {
            return new String(bytes, encoding);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public String base64() {
        return StringHelper.encodeBase64(bytes);
    }

    /**
     * Returns this byte string encoded as <a href="http://www.ietf.org/rfc/rfc4648.txt">URL-safe Base64</a>.
     */
    public String base64Url() {
        return StringHelper.encodeBase64Url(bytes);
    }

    public String toDataUrl(String mimeType) {
        return "data:" + mimeType + ";base64," + base64();
    }

    /**
     * toImageUrl("gif") 返回data:image/gif;base64,xxxx
     *
     * @param imageType 文件类型，例如gif,png等
     * @return 前台浏览器可以识别的data url链接
     */
    public String toImageUrl(String imageType) {
        return toDataUrl("image/" + imageType);
    }

    /**
     * Returns the 128-bit MD5 hash of this byte string.
     */
    public ByteString md5() {
        return ByteString.of(HashHelper.md5(bytes));
    }

    /**
     * Returns the 160-bit SHA-1 hash of this byte string.
     */
    public ByteString sha1() {
        return ByteString.of(HashHelper.sha1(bytes));
    }

    /**
     * Returns the 256-bit SHA-256 hash of this byte string.
     */
    public ByteString sha256() {
        return ByteString.of(HashHelper.sha256(bytes, null));
    }

    /**
     * Returns the 512-bit SHA-512 hash of this byte string.
     */
    public ByteString sha512() {
        return ByteString.of(HashHelper.sha512(bytes, null));
    }

    /**
     * Returns the 160-bit SHA-1 HMAC of this byte string.
     */
    public ByteString hmacSha1(ByteString key) {
        return hmac("HmacSHA1", key);
    }

    /**
     * Returns the 256-bit SHA-256 HMAC of this byte string.
     */
    public ByteString hmacSha256(ByteString key) {
        return hmac("HmacSHA256", key);
    }

    /**
     * Returns the 512-bit SHA-512 HMAC of this byte string.
     */
    public ByteString hmacSha512(ByteString key) {
        return hmac("HmacSHA512", key);
    }

    private ByteString hmac(String algorithm, ByteString key) {
        return ByteString.of(HashHelper.hmac(algorithm, bytes, key.toByteArray()));
    }

    public static ByteString of(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return EMPTY;
        return new ByteString(bytes);
    }


    public static ByteString decodeBase64(String base64) {
        if (base64 == null)
            return null;
        byte[] decoded = StringHelper.decodeBase64(base64);
        return decoded != null ? new ByteString(decoded) : null;
    }

    @StaticFactoryMethod
    public static ByteString parseEncodedString(String str) {
        if (str == null)
            return null;
        if (str.startsWith(CommonConstants.BASE_64_PREFIX))
            return decodeBase64(str.substring(CommonConstants.BASE_64_PREFIX.length()));
        if (str.startsWith(CommonConstants.HEX_PREFIX))
            return decodeHex(str.substring(CommonConstants.HEX_PREFIX.length()));
        if(str.startsWith(CommonConstants.HEX_BYTES_PREFIX))
            return decodeHex(str.substring(CommonConstants.HEX_BYTES_PREFIX.length()));
        if (str.startsWith(CommonConstants.UTF8_PREFIX))
            return new ByteString(str.substring(CommonConstants.UTF8_PREFIX.length()));
        return new ByteString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes the hex-encoded bytes and returns their value a byte string.
     */
    public static ByteString decodeHex(String hex) {
        return of(StringHelper.hexToBytes(hex));
    }

    /**
     * Reads {@code count} bytes from {@code in} and returns the result.
     *
     * @throws java.io.EOFException if {@code in} has fewer than {@code count} bytes to read.
     */
    public static ByteString read(InputStream in, int byteCount) throws IOException {
        if (in == null)
            throw new IllegalArgumentException("in == null");
        if (byteCount < 0)
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);

        byte[] result = new byte[byteCount];
        for (int offset = 0, read; offset < byteCount; offset += read) {
            read = in.read(result, offset, byteCount - offset);
            if (read == -1)
                throw new EOFException();
        }
        return ByteString.of(result);
    }

    /**
     * Writes the contents of this byte string to {@code out}.
     */
    public void write(OutputStream out) throws IOException {
        if (out == null)
            throw new IllegalArgumentException("out == null");
        out.write(bytes);
    }

    /**
     * Returns a byte string that is a substring of this byte string, beginning at the specified index until the end of
     * this string. Returns this byte string if {@code beginIndex} is 0.
     */
    public ByteString substring(int beginIndex) {
        return substring(beginIndex, bytes.length);
    }

    /**
     * Returns a byte string that is a substring of this byte string, beginning at the specified {@code beginIndex} and
     * ends at the specified {@code endIndex}. Returns this byte string if {@code beginIndex} is 0 and {@code endIndex}
     * is the length of this byte string.
     */
    public ByteString substring(int beginIndex, int endIndex) {
        if (beginIndex < 0)
            throw new IllegalArgumentException("beginIndex < 0");
        if (endIndex > bytes.length) {
            throw new IllegalArgumentException("endIndex > length(" + bytes.length + ")");
        }

        int subLen = endIndex - beginIndex;
        if (subLen < 0)
            throw new IllegalArgumentException("endIndex < beginIndex");

        if (beginIndex == 0 && endIndex == bytes.length) {
            return this;
        }

        byte[] copy = new byte[subLen];
        System.arraycopy(bytes, beginIndex, copy, 0, subLen);
        return new ByteString(copy);
    }

    /**
     * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of {@code other} in
     * {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is out of bounds.
     */
    public boolean rangeEquals(int offset, ByteString other, int otherOffset, int byteCount) {
        return other.rangeEqualsBytes(otherOffset, this.bytes, offset, byteCount);
    }

    /**
     * Returns true if the bytes of this in {@code [offset..offset+byteCount)} equal the bytes of {@code other} in
     * {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is out of bounds.
     */
    public boolean rangeEqualsBytes(int offset, byte[] other, int otherOffset, int byteCount) {
        return offset >= 0 && offset <= bytes.length - byteCount && otherOffset >= 0
                && otherOffset <= other.length - byteCount
                && arrayRangeEquals(bytes, offset, other, otherOffset, byteCount);
    }

    public boolean startsWith(ByteString prefix) {
        return rangeEquals(0, prefix, 0, prefix.length());
    }

    public boolean startsWithBytes(byte[] prefix) {
        return rangeEqualsBytes(0, prefix, 0, prefix.length);
    }

    public boolean endsWith(ByteString suffix) {
        return rangeEquals(length() - suffix.length(), suffix, 0, suffix.length());
    }

    public boolean endsWithBytes(byte[] suffix) {
        return rangeEqualsBytes(length() - suffix.length, suffix, 0, suffix.length);
    }

    public ByteString append(ByteString str) {
        byte[] ret = new byte[length() + str.length()];
        System.arraycopy(bytes, 0, ret, 0, length());
        System.arraycopy(str.bytes, 0, ret, length(), str.length());
        return new ByteString(ret);
    }

    public ByteString leftPad(int length, byte c) {
        if (length() >= length)
            return this;
        byte[] ret = new byte[length];
        System.arraycopy(bytes, 0, ret, length - length(), length());
        for (int i = 0, n = length - length(); i < n; i++) {
            ret[i] = c;
        }
        return new ByteString(ret);
    }

    public ByteString rightPad(int length, byte c) {
        if (length() >= length)
            return this;
        byte[] ret = new byte[length];
        System.arraycopy(bytes, 0, ret, 0, length());
        for (int i = length(), n = length; i < n; i++)
            ret[i] = c;
        return new ByteString(ret);
    }

    public int indexOf(ByteString other) {
        return indexOfBytes(other.bytes, 0);
    }

    public int indexOf(ByteString other, int fromIndex) {
        return indexOfBytes(other.bytes, fromIndex);
    }

    public int indexOfBytes(byte[] other) {
        return indexOfBytes(other, 0);
    }

    public int indexOfBytes(byte[] other, int fromIndex) {
        fromIndex = Math.max(fromIndex, 0);
        for (int i = fromIndex, limit = bytes.length - other.length; i <= limit; i++) {
            if (arrayRangeEquals(bytes, i, other, 0, other.length)) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(ByteString other) {
        return lastIndexOfBytes(other.bytes, length());
    }

    public int lastIndexOf(ByteString other, int fromIndex) {
        return lastIndexOfBytes(other.bytes, fromIndex);
    }

    public int lastIndexOfBytes(byte[] other) {
        return lastIndexOfBytes(other, length());
    }

    public int lastIndexOfBytes(byte[] other, int fromIndex) {
        fromIndex = Math.min(fromIndex, bytes.length - other.length);
        for (int i = fromIndex; i >= 0; i--) {
            if (arrayRangeEquals(bytes, i, other, 0, other.length)) {
                return i;
            }
        }
        return -1;
    }

    static boolean arrayRangeEquals(byte[] a, int aOffset, byte[] b, int bOffset, int byteCount) {
        return ByteHelper.equals(a, aOffset, b.length - aOffset, b, bOffset, byteCount);
    }
}