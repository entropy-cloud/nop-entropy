/*
 * Copyright 2023-2024 the original author or authors.
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

package io.nop.ai.core.api.support;

import io.nop.ai.core.api.support.Metadata;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.Nullable;

import java.net.URL;


/**
 * The Media class represents the data and metadata of a media attachment in a message. It
 * consists of a MIME type, raw data, and optional metadata such as id and name.
 *
 * <p>
 * Media objects can be used in the UserMessage class to attach various types of content
 * like images, documents, or videos. When interacting with AI models, the id and name
 * fields help track and reference specific media objects.
 *
 * <p>
 * The id field is typically assigned by AI models when they reference previously provided
 * media.
 *
 * <p>
 * The name field can be used to provide a descriptive identifier to the model, though
 * care should be taken to avoid prompt injection vulnerabilities. For amazon AWS the name
 * must only contain:
 * <ul>
 * <li>Alphanumeric characters
 * <li>Whitespace characters (no more than one in a row)
 * <li>Hyphens
 * <li>Parentheses
 * <li>Square brackets
 * </ul>
 * Note, this class does not directly enforce that restriction.
 *
 * <p>
 * If no name is provided, one will be automatically generated using the pattern:
 * {@code {mimeType.subtype}-{UUID}}
 * <p>
 * This class is used as a parameter in the constructor of the UserMessage class.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @since 1.0.0
 */
@DataBean
public class Media extends Metadata {

    private static final String NAME_PREFIX = "media-";

    /**
     * An Id of the media object, usually defined when the model returns a reference to
     * media it has been passed.
     */
    @Nullable
    private String id;

    private String mimeType;

    private Object data;

    /**
     * The name of the media object that can be referenced by the AI model.
     * <p>
     * Important security note: This field is vulnerable to prompt injections, as the
     * model might inadvertently interpret it as instructions. It is recommended to
     * specify neutral names.
     *
     * <p>
     * The name must only contain:
     * <ul>
     * <li>Alphanumeric characters
     * <li>Whitespace characters (no more than one in a row)
     * <li>Hyphens
     * <li>Parentheses
     * <li>Square brackets
     * </ul>
     */
    private String name;

    /**
     * Create a new Media instance.
     *
     * @param mimeType the media MIME type
     * @param url      the URL for the media data
     */
    public Media(String mimeType, URL url) {
        Guard.notNull(mimeType, "MimeType must not be null");
        Guard.notNull(url, "URL must not be null");
        this.mimeType = mimeType;
        this.id = null;
        this.data = url.toString();
        this.name = generateDefaultName(mimeType);
    }

    private static String generateDefaultName(String mimeType) {
        return NAME_PREFIX + StringHelper.lastPart(mimeType, '-') + "-" + java.util.UUID.randomUUID();
    }

    /**
     * Get the media MIME type
     *
     * @return the media MIME type
     */
    public String getMimeType() {
        return this.mimeType;
    }

    /**
     * Get the media data object
     *
     * @return a java.net.URL.toString() or a byte[]
     */
    public Object getData() {
        return this.data;
    }

    /**
     * Get the media data as a byte array
     *
     * @return the media data as a byte array
     */
    public byte[] getDataAsByteArray() {
        if (this.data instanceof byte[]) {
            return (byte[]) this.data;
        } else {
            throw new IllegalStateException("Media data is not a byte[]");
        }
    }

    /**
     * Get the media id
     *
     * @return the media id
     */
    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setName(String name) {
        this.name = name;
    }
}
