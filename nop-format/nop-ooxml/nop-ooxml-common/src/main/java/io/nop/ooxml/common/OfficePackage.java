/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.store.LocalResourceStore;
import io.nop.core.resource.store.ZipResourceStore;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.ooxml.common.impl.ResourceOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.common.model.PackagePartName;
import io.nop.ooxml.common.model.PackagingURIHelper;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_FILE_PATH_MUST_HAS_EXT;

public class OfficePackage implements Closeable, ISourceLocationGetter {
    private SourceLocation location;

    private IResourceStore resourceStore;

    private final TreeMap<String, IOfficePackagePart> files = new TreeMap<>();

    private final Map<String, MutableInt> nextIndex = new HashMap<>();

    public OfficePackage() {
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public void loadFromFile(File file) {
        close();

        this.location = SourceLocation.fromPath(FileHelper.getFileUrl(file));

        IResourceStore store;
        if (file.isDirectory()) {
            store = new LocalResourceStore("/", file);
        } else {
            store = ZipResourceStore.build(file, "/", "/");
        }
        this.resourceStore = store;
        collectFiles("", resourceStore.getResource("/"));
    }

    public void loadFromResource(IResource resource) {
        close();

        File file = resource.toFile();
        if (file != null) {
            loadFromFile(file);
        } else {
            InMemoryResourceStore store = new InMemoryResourceStore();
            store.addZipFile("", resource);
            this.resourceStore = store;
            collectFiles("", resourceStore.getResource("/"));
        }
    }

    public List<IOfficePackagePart> getFiles(String prefix) {
        List<IOfficePackagePart> ret = new ArrayList<>();
        files.forEach((path, part) -> {
            if (path.startsWith(prefix)) {
                ret.add(part);
            }
        });
        return ret;
    }

    public OfficePackage copy() {
        OfficePackage pkg = new OfficePackage();
        pkg.setLocation(location);
        copyTo(pkg);
        return pkg;
    }

    public void copyTo(OfficePackage pkg) {
        pkg.files.putAll(files);
    }

    public OfficePackage loadInMemory() {
        if (this.resourceStore != null) {
            for (Map.Entry<String, IOfficePackagePart> entry : files.entrySet()) {
                IOfficePackagePart part = entry.getValue();
                part = part.loadInMemory();
                entry.setValue(part);
            }
            IoHelper.safeClose(resourceStore);
            this.resourceStore = null;
        }
        return this;
    }

    private void collectFiles(String parentPath, IResource resource) {
        List<? extends IResource> files = resourceStore.getChildren(resource.getStdPath());
        if (files != null) {
            for (IResource file : files) {
                if (file.getName().startsWith("[trash]"))
                    continue;

                String basePath = StringHelper.appendPath(parentPath, file.getName());
                if (file.isDirectory()) {
                    collectFiles(basePath, file);
                } else {
                    addFile(new ResourceOfficePackagePart(basePath, file));
                }
            }
        }
    }

    @Override
    public void close() {
        IoHelper.safeClose(resourceStore);
        this.resourceStore = null;
    }

    public Collection<IOfficePackagePart> getFiles() {
        return files.values();
    }

    public IOfficePackagePart getFile(String path) {
        if (path.startsWith("/"))
            path = path.substring(1);
        return files.get(path);
    }

    public IOfficePackagePart getPart(String path){
        return getFile(path);
    }

    public ResourceOfficePackagePart addFile(String path, IResource resource) {
        if (path.startsWith("/"))
            path = path.substring(1);
        ResourceOfficePackagePart part = new ResourceOfficePackagePart(path, resource);
        addFile(part);
        return part;
    }

    /**
     * 必须新建文件。如果文件名对应的文件已存在，则尝试增加index。例如加入path=media/image1.png，如果image1.png已经存在， 则实际最终对应的path可能是media/image2.png
     *
     * @param path     文件在最终ooxml压缩包中的路径。如果该路径对应的文件已存在，则尝试增加index，生成新的文件路径
     * @param resource 文件资源
     * @return 新生成的文件路径
     */
    public String addNewFile(String path, IResource resource) {
        int pos = path.lastIndexOf('.');
        if (pos < 0) {
            throw new NopException(ERR_OOXML_FILE_PATH_MUST_HAS_EXT).param(ARG_PATH, path);
        }

        int startPos = pos - 1;
        while (startPos >= 0) {
            if (!StringHelper.isDigit(path.charAt(startPos))) {
                break;
            }
            startPos--;
        }
        String prefix = path.substring(0, startPos + 1);
        String ext = path.substring(pos);

        MutableInt idx = nextIndex.computeIfAbsent(prefix, k -> new MutableInt(2));
        do {
            String newPath = prefix + idx + ext;
            if (!files.containsKey(newPath)) {
                addFile(newPath, resource);
                return newPath;
            }
            idx.incrementAndGet();
        } while (true);
    }

    public IOfficePackagePart removeFile(String path) {
        return files.remove(path);
    }

    public void addFile(IOfficePackagePart file) {
        files.put(file.getPath(), file);
    }

    public OfficeRelsPart getRels(String path) {
        IOfficePackagePart file = files.get(path);
        if (file == null)
            return null;

        if (file instanceof OfficeRelsPart)
            return (OfficeRelsPart) file;

        ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;
        OfficeRelsPart rels = OfficeRelsPart.parse(file.getPath(), res.getResource());
        files.put(path, rels);
        return rels;
    }

    public OfficeRelsPart makeRels(String path) {
        OfficeRelsPart part = getRels(path);
        if (part == null) {
            part = new OfficeRelsPart(path);
            files.put(path, part);
        }
        return part;
    }

    public ContentTypesPart getContentTypes() {
        IOfficePackagePart part = files.get(ContentTypesPart.CONTENT_TYPES_PART_NAME);
        if (part == null) {
            part = new ContentTypesPart();
            files.put(ContentTypesPart.CONTENT_TYPES_PART_NAME, part);
        }

        if (!(part instanceof ContentTypesPart)) {
            XNode node = part.buildXml(null);
            ContentTypesPart contentType = new ContentTypesPart();
            contentType.parseContentTypes(node);
            part = contentType;
            files.put(ContentTypesPart.CONTENT_TYPES_PART_NAME, part);
        }

        return (ContentTypesPart) part;
    }

    public IOfficePackagePart getPartByContentType(String contentType) {
        ContentTypesPart contentTypes = getContentTypes();
        PackagePartName partName = contentTypes.getPartWithContentType(contentType);
        if (partName == null)
            return null;
        String name = partName.getName();
        if (name.startsWith("/"))
            name = name.substring(1);
        return files.get(name);
    }

    public OfficeRelsPart getRelsForPart(IOfficePackagePart part) {
        String relsPath = PackagingURIHelper.getRelationshipPartName(part.getPath());
        return getRels(relsPath);
    }

    public OfficeRelsPart getRelsForPartPath(String path) {
        String relsPath = PackagingURIHelper.getRelationshipPartName(path);
        return getRels(relsPath);
    }

    public OfficeRelsPart makeRelsForPart(IOfficePackagePart part) {
        String relsPath = PackagingURIHelper.getRelationshipPartName(part.getPath());
        return makeRels(relsPath);
    }

    public OfficeRelsPart makeRelsForPartPath(String partPath) {
        String relsPath = PackagingURIHelper.getRelationshipPartName(partPath);
        return makeRels(relsPath);
    }

    public IOfficePackagePart getRelPart(IOfficePackagePart part, String relId) {
        OfficeRelsPart rels = getRelsForPart(part);
        if (rels == null)
            return null;
        OfficeRelationship rel = rels.getRelationship(relId);
        if (rel == null)
            return null;

        String fullPath = StringHelper.absolutePath(part.getPath(), rel.getTarget());
        return getFile(fullPath);
    }

    public IOfficePackagePart getRelPartByType(IOfficePackagePart part, String relType) {
        OfficeRelsPart rels = getRelsForPart(part);
        if (rels == null)
            return null;
        OfficeRelationship rel = rels.getRelationshipByType(relType);
        if (rel == null)
            return null;
        String fullPath = StringHelper.absolutePath(part.getPath(), rel.getTarget());
        return getFile(fullPath);
    }

    public void generateToZip(IZipOutput out, IEvalScope scope) throws IOException {
        for (IOfficePackagePart file : files.values()) {
            file.generateToZip(out, scope);
        }
    }

    public void generateToDir(File dir, IEvalScope scope) {
        for (IOfficePackagePart file : files.values()) {
            file.generateToFile(new File(dir, file.getPath()), scope);
        }
    }

    public void saveToFile(File file, IEvalScope scope) {
        saveToResource(new FileResource(file), scope);
    }

    public void saveToResource(IResource resource, IEvalScope scope) {
        ZipOptions options = new ZipOptions();
        String password = (String) scope.getValue(OfficeConstants.VAR_FILE_PASSWORD);
        options.setPassword(password);
        OutputStream os = resource.getOutputStream();
        try {
            IZipOutput out = ResourceHelper.getZipTool().newZipOutput(os, options);
            generateToZip(out, scope);
            out.close();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    // public static OfficePackage load(File file) {
    // OfficePackage pkg = new OfficePackage();
    // pkg.loadFrom(file);
    // return pkg;
    // }
}