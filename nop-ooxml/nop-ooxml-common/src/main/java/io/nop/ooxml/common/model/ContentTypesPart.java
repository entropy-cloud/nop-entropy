/* ====================================================================
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
==================================================================== */

package io.nop.ooxml.common.model;

import io.nop.core.CoreConstants;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.constants.PackageNamespaces;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manage package content types ([Content_Types].xml part).
 */
public class ContentTypesPart implements IOfficePackagePart {

    /**
     * Content type part name.
     */
    public static final String CONTENT_TYPES_PART_NAME = "[Content_Types].xml";

    // public static final String CONTENT_TYPES_PART_PATH = "/[Content_Types].xml";

    /**
     * Content type namespace
     */
    public static final String TYPES_NAMESPACE_URI = PackageNamespaces.CONTENT_TYPES;

    /* Xml elements in content type part */

    private static final String TYPES_TAG_NAME = "Types";

    private static final String DEFAULT_TAG_NAME = "Default";

    private static final String EXTENSION_ATTRIBUTE_NAME = "Extension";

    private static final String CONTENT_TYPE_ATTRIBUTE_NAME = "ContentType";

    private static final String OVERRIDE_TAG_NAME = "Override";

    private static final String PART_NAME_ATTRIBUTE_NAME = "PartName";

    /**
     * Default content type tree. <Extension, ContentType>
     */
    private TreeMap<String, String> defaultContentType = new TreeMap<>();

    /**
     * Override content type tree.
     */
    private TreeMap<PackagePartName, String> overrideContentType;

    //private Map<String, List<PackagePartName>> contentTypeToParts;

    /**
     * Build association extension-&gt; content type (will be stored in [Content_Types].xml) for example
     * ContentType="image/png" Extension="png"
     * <p>
     * [M2.8]: When adding a new part to a package, the package implementer shall ensure that a content type for that
     * part is specified in the Content Types stream; the package implementer shall perform the steps described in
     * &#167;9.1.2.3:
     * </p>
     * <p>
     * 1. Get the extension from the part name by taking the substring to the right of the rightmost occurrence of the
     * dot character (.) from the rightmost segment.
     * </p>
     * <p>
     * 2. If a part name has no extension, a corresponding Override element shall be added to the Content Types stream.
     * </p>
     * <p>
     * 3. Compare the resulting extension with the values specified for the Extension attributes of the Default elements
     * in the Content Types stream. The comparison shall be case-insensitive ASCII.
     * </p>
     * <p>
     * 4. If there is a Default element with a matching Extension attribute, then the content type of the new part shall
     * be compared with the value of the ContentType attribute. The comparison might be case-sensitive and include every
     * character regardless of the role it plays in the content-type grammar of RFC 2616, or it might follow the grammar
     * of RFC 2616.
     * </p>
     * <p>
     * a. If the content types match, no further action is required.
     * </p>
     * <p>
     * b. If the content types do not match, a new Override element shall be added to the Content Types stream. .
     * </p>
     * <p>
     * 5. If there is no Default element with a matching Extension attribute, a new Default element or Override element
     * shall be added to the Content Types stream.
     * </p>
     */
    public void addContentType(PackagePartName partName, String contentType) {
        boolean defaultCTExists = this.defaultContentType.containsValue(contentType);
        String extension = partName.getExtension().toLowerCase(Locale.ROOT);
        if ((extension.length() == 0) ||
                // check if content-type and extension do match in both directions
                // some applications create broken files, e.g. extension "jpg" instead of "jpeg"
                (this.defaultContentType.containsKey(extension) && !defaultCTExists)
                || (!this.defaultContentType.containsKey(extension) && defaultCTExists)) {
            this.addOverrideContentType(partName, contentType);
        } else if (!defaultCTExists) {
            this.addDefaultContentType(extension, contentType);
        }
    }

    /**
     * Add an override content type for a specific part.
     *
     * @param partName    Name of the part.
     * @param contentType Content type of the part.
     */
    private void addOverrideContentType(PackagePartName partName, String contentType) {
        if (overrideContentType == null) {
            overrideContentType = new TreeMap<>();
        }
        overrideContentType.put(partName, contentType);
    }

    /**
     * Add a content type associated with the specified extension.
     *
     * @param extension   The part name extension to bind to a content type.
     * @param contentType The content type associated with the specified extension.
     */
    public void addDefaultContentType(String extension, String contentType) {
        // Remark : Originally the latest parameter was :
        // contentType.toLowerCase(). Change due to a request ID 1996748.
        defaultContentType.put(extension.toLowerCase(Locale.ROOT), contentType);
    }

    /**
     * <p>
     * Delete a content type based on the specified part name. If the specified part name is register with an override
     * content type, then this content type is remove, else the content type is remove in the default content type list
     * if it exists and if no part is associated with it yet.
     * </p>
     * <p>
     * Check rule M2.4: The package implementer shall require that the Content Types stream contain one of the following
     * for every part in the package: One matching Default element One matching Override element Both a matching Default
     * element and a matching Override element, in which case the Override element takes precedence.
     * </p>
     *
     * @param partName The part URI associated with the override content type to delete.
     */
    public void removeContentType(PackagePartName partName) {
        if (partName == null) {
            throw new IllegalArgumentException("partName");
        }

        /* Override content type */
        if (this.overrideContentType != null && (this.overrideContentType.get(partName) != null)) {
            // Remove the override definition for the specified part.
            this.overrideContentType.remove(partName);
            return;
        }

        /* Default content type */
        String extensionToDelete = partName.getExtension();
        boolean deleteDefaultContentTypeFlag = true;
        // if (this.container != null) {
        // try {
        // for (PackagePart part : this.container.getParts()) {
        // if (!part.getPartName().equals(partName)
        // && part.getPartName().getExtension()
        // .equalsIgnoreCase(extensionToDelete)) {
        // deleteDefaultContentTypeFlag = false;
        // break;
        // }
        // }
        // } catch (InvalidFormatException e) {
        // throw new InvalidOperationException(e.getMessage());
        // }
        // }

        // Remove the default content type, no other part use this content type.
        if (deleteDefaultContentTypeFlag) {
            this.defaultContentType.remove(extensionToDelete);
        }

        /*
         * Check rule 2.4: The package implementer shall require that the Content Types stream contain one of the
         * following for every part in the package: One matching Default element One matching Override element Both a
         * matching Default element and a matching Override element, in which case the Override element takes
         * precedence.
         */
        // if (this.container != null) {
        // try {
        // for (PackagePart part : this.container.getParts()) {
        // if (!part.getPartName().equals(partName)
        // && this.getContentType(part.getPartName()) == null) {
        // throw new InvalidOperationException(
        // "Rule M2.4 is not respected: Nor a default element or override element is associated with the part: "
        // + part.getPartName().getName());
        // }
        // }
        // } catch (InvalidFormatException e) {
        // throw new InvalidOperationException(e.getMessage());
        // }
        // }
    }

    /**
     * Check if the specified content type is already register.
     *
     * @param contentType The content type to check.
     * @return <code>true</code> if the specified content type is already register, then <code>false</code>.
     */
    public boolean isContentTypeRegister(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("contentType");
        }

        return (this.defaultContentType.containsValue(contentType)
                || (this.overrideContentType != null && this.overrideContentType.containsValue(contentType)));
    }

    public List<PackagePartName> getPartsWithContentType(String contentType) {
        List<PackagePartName> parts = new ArrayList<>();
        if (overrideContentType != null) {
            for (Map.Entry<PackagePartName, String> entry : this.overrideContentType.entrySet()) {
                if (entry.getValue().equals(contentType)) {
                    parts.add(entry.getKey());
                }
            }
        }
        return parts;
    }

    public PackagePartName getPartWithContentType(String contentType) {
        if (overrideContentType != null) {
            for (Map.Entry<PackagePartName, String> entry : this.overrideContentType.entrySet()) {
                if (entry.getValue().equals(contentType)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Get the content type for the specified part, if any.
     * <p>
     * Rule [M2.9]: To get the content type of a part, the package implementer shall perform the steps described in
     * &#167;9.1.2.4:
     * </p>
     * <p>
     * 1. Compare the part name with the values specified for the PartName attribute of the Override elements. The
     * comparison shall be case-insensitive ASCII.
     * </p>
     * <p>
     * 2. If there is an Override element with a matching PartName attribute, return the value of its ContentType
     * attribute. No further action is required.
     * </p>
     * <p>
     * 3. If there is no Override element with a matching PartName attribute, then a. Get the extension from the part
     * name by taking the substring to the right of the rightmost occurrence of the dot character (.) from the rightmost
     * segment. b. Check the Default elements of the Content Types stream, comparing the extension with the value of the
     * Extension attribute. The comparison shall be case-insensitive ASCII.
     * </p>
     * <p>
     * 4. If there is a Default element with a matching Extension attribute, return the value of its ContentType
     * attribute. No further action is required.
     * </p>
     * <p>
     * 5. If neither Override nor Default elements with matching attributes are found for the specified part name, the
     * implementation shall not map this part name to a part.
     * </p>
     *
     * @param partName The URI part to check.
     * @return The content type associated with the URI (in case of an override content type) or the extension (in case
     * of default content type), else <code>null</code>.
     */
    public String getContentType(PackagePartName partName) {
        if (partName == null) {
            throw new IllegalArgumentException("partName");
        }

        if ((this.overrideContentType != null) && this.overrideContentType.containsKey(partName)) {
            return this.overrideContentType.get(partName);
        }

        String extension = partName.getExtension().toLowerCase(Locale.ROOT);
        if (this.defaultContentType.containsKey(extension)) {
            return this.defaultContentType.get(extension);
        }

        /*
         * [M2.4] : The package implementer shall require that the Content Types stream contain one of the following for
         * every part in the package: One matching Default element, One matching Override element, Both a matching
         * Default element and a matching Override element, in which case the Override element takes precedence.
         */
        // if (this.container != null && this.container.getPart(partName) != null) {
        // throw new OpenXML4JRuntimeException(
        // "Rule M2.4 exception : Part \'"
        // + partName
        // + "\' not found - this error should NEVER happen!\n"
        // + "Check that your code is closing the open resources in the correct order prior to filing a bug report.\n"
        // + "If you can provide the triggering file, then please raise a bug at
        // https://bz.apache.org/bugzilla/enter_bug.cgi?product=POI and attach the file that triggers it, thanks!");
        // }
        return null;
    }

    /**
     * Clear all content types.
     */
    public void clearAll() {
        this.defaultContentType.clear();
        if (this.overrideContentType != null) {
            this.overrideContentType.clear();
        }
    }

    /**
     * Clear all override content types.
     */
    public void clearOverrideContentTypes() {
        if (this.overrideContentType != null) {
            this.overrideContentType.clear();
        }
    }

    /**
     * Parse the content types part.
     */
    public void parseContentTypes(XNode node) {
        for (XNode child : node.getChildren()) {
            String tagName = child.getTagName();
            if (tagName.equals(DEFAULT_TAG_NAME)) {
                String extension = child.attrText(EXTENSION_ATTRIBUTE_NAME);
                String contentType = child.attrText(CONTENT_TYPE_ATTRIBUTE_NAME);
                addDefaultContentType(extension, contentType);
            } else if (tagName.equals(OVERRIDE_TAG_NAME)) {
                String partNameStr = child.attrText(PART_NAME_ATTRIBUTE_NAME);
                PackagePartName partName = PackagingURIHelper.createPartName(partNameStr);
                String contentType = child.attrText(CONTENT_TYPE_ATTRIBUTE_NAME);
                addOverrideContentType(partName, contentType);
            }
        }
    }

    public String getPath() {
        return CONTENT_TYPES_PART_NAME;
    }

    public XNode loadXml() {
        XNode node = XNode.make(TYPES_TAG_NAME);
        node.setAttr(CoreConstants.ATTR_XMLNS, TYPES_NAMESPACE_URI);

        for (Map.Entry<String, String> entry : defaultContentType.entrySet()) {
            XNode child = XNode.make(DEFAULT_TAG_NAME);
            child.setAttr(EXTENSION_ATTRIBUTE_NAME, entry.getKey());
            child.setAttr(CONTENT_TYPE_ATTRIBUTE_NAME, entry.getValue());
            node.appendChild(child);
        }

        if (overrideContentType != null) {
            for (Map.Entry<PackagePartName, String> entry : overrideContentType.entrySet()) {
                PackagePartName partName = entry.getKey();
                String contentType = entry.getValue();

                XNode child = XNode.make(OVERRIDE_TAG_NAME);
                child.setAttr(PART_NAME_ATTRIBUTE_NAME, partName.getName());
                child.setAttr(CONTENT_TYPE_ATTRIBUTE_NAME, contentType);
                node.appendChild(child);
            }
        }

        return node;
    }

    public XNode buildXml(IEvalContext context) {
        return loadXml();
    }
}
