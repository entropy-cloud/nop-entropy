/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.tree.ITreeStateVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.ResourceTreeVisitState;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.DelegateResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.scan.ClassPathScanner;
import io.nop.core.resource.scan.FileScanHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import static io.nop.core.CoreConfigs.CFG_CHECK_DUPLICATE_VFS_RESOURCE;
import static io.nop.core.CoreConfigs.CFG_INCLUDE_CURRENT_PROJECT_RESOURCES;
import static io.nop.core.CoreConfigs.CFG_USE_NOP_VFS_INDEX;
import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ARG_RESOURCE1;
import static io.nop.core.CoreErrors.ARG_RESOURCE2;
import static io.nop.core.CoreErrors.ERR_RESOURCE_DUPLICATE_VFS_RESOURCE;

public class DeltaResourceStoreBuilder implements IDeltaResourceStoreBuilder {
    static final Logger LOG = LoggerFactory.getLogger(DeltaResourceStoreBuilder.class);

    private List<ZipFile> zipFiles = new ArrayList<>();
    private Set<String> classPathFiles = new TreeSet<>();

    public DeltaResourceStoreBuilder() {
    }

    @Override
    public List<ZipFile> getZipFiles() {
        return zipFiles;
    }

    @Override
    public DeltaResourceStore build(VfsConfig config) {
        DeltaResourceStore store = new DeltaResourceStore();
        store.setClassPathFiles(classPathFiles);
        store.setDeltaLayerIds(config.getDeltaLayerIds());

        store.setStore(buildBaseStore(config));

        // 如果存在default delta层，则直接使用它
        if (CollectionHelper.isEmpty(config.getDeltaLayerIds())) {
            if (store.getStore().getResource(ResourceConstants.DELTA_PATH_PREFIX + "default").exists()) {
                store.setDeltaLayerIds(Collections.singletonList("default"));
            }
        }

        return store;
    }

    protected IResourceStore buildBaseStore(VfsConfig config) {
        IResourceStore store = buildCompositeStore(config);
        File dir = ResourceHelper.getOverrideVFsDir();
        if (dir == null)
            return store;

        LOG.info("nop.vfs.use-override-fs-dir:{}", FileHelper.getAbsolutePath(dir));

        LocalResourceStore vfsStore = new LocalResourceStore("/", dir);
        store = new OverrideResourceStore(vfsStore, store);
        return store;
    }

    protected IResourceStore buildCompositeStore(VfsConfig config) {
        if (config.getPathMappings() == null || config.getPathMappings().isEmpty()) {
            return buildInMemoryStore(config);
        }
        CompositeResourceStore store = new CompositeResourceStore(buildInMemoryStore(config));
        for (Map.Entry<String, String> mapping : config.getPathMappings().entrySet()) {
            String basePath = mapping.getKey();
            if (!basePath.endsWith("/")) {
                basePath += "/";
            }
            File dir = new File(mapping.getValue());
            IResourceStore subStore = new LocalResourceStore(basePath, dir);
            store.addStore(basePath, subStore);
        }
        return store;
    }

    protected IResourceStore buildInMemoryStore(VfsConfig config) {
        InMemoryResourceStore store = new InMemoryResourceStore();

        IResource indexResource = new ClassPathResource(ResourceConstants.RESOURCE_VFS_INDEX);
        if (CFG_USE_NOP_VFS_INDEX.get() && indexResource.exists()) {
            String[] files = StringHelper.splitToLines(ResourceHelper.readText(indexResource));
            for (String file : files) {
                ClassPathResource resource = new ClassPathResource("classpath:_vfs" + file);
                if (resource.exists()) {
                    store.addResource(normalizeResource(new DelegateResource(file, resource)));

                    if (ReflectionManager.instance().isRecordForNativeImage()) {
                        classPathFiles.add(file);
                    }
                }
            }
        }

        if (config.isScanClassPath()) {
            new ClassPathScanner().scanPath(ResourceConstants.CLASS_PATH_VFS_DIR, (path, url) -> {
                path = path.substring(ResourceConstants.CLASS_PATH_VFS_DIR.length() - 1);
                String fileName = StringHelper.fileFullName(path);
                if (fileName.startsWith("~"))
                    return;

                LOG.trace("nop.vfs.add:path={},url={}", path, url);
                if (ReflectionManager.instance().isRecordForNativeImage()) {
                    classPathFiles.add(path);
                }

                IResource resource = ResourceHelper.buildResourceFromURL(path, url);

                resource = normalizeResource(resource);
                boolean b = store.addResourceIfAbsent(resource);
                if (!b && !isAllowDuplicate(path) && CFG_CHECK_DUPLICATE_VFS_RESOURCE.get()) {
                    throw new NopException(ERR_RESOURCE_DUPLICATE_VFS_RESOURCE).param(ARG_PATH, path)
                            .param(ARG_RESOURCE2, resource).param(ARG_RESOURCE1, store.getResource(path));
                }
            });
        }

        if (config.getLibPaths() != null) {
            for (String libPath : config.getLibPaths()) {
                File libFile = new File(libPath);
                if (libFile.isDirectory()) {
                    LOG.info("nop.vfs.add-lib-dir:{}", libFile.getAbsolutePath());
                    if (libFile.getName().equals(ResourceConstants.VFS_DIR_NAME)) {
                        store.addFileDir("/", libFile);
                    } else {
                        store.addFileDir("/", new File(libFile, ResourceConstants.CLASS_PATH_VFS_DIR));
                    }
                } else {
                    LOG.info("nop.vfs.add-lib-jar:{}", libFile.getAbsolutePath());
                    ZipFile zipFile = newZipFile(libFile);
                    FileScanHelper.scanZip(zipFile, ResourceConstants.CLASS_PATH_VFS_DIR, "/", store);
                }
            }
        }

        if (CFG_INCLUDE_CURRENT_PROJECT_RESOURCES.get()) {
            File dir = MavenDirHelper.guessProjectDir();
            if (dir.exists()) {
                File vfsDir = FileHelper.getAbsoluteFile(new File(dir, "src/main/resources/_vfs"));
                if (vfsDir.exists()) {
                    LOG.info("nop.resource.use-current-project-resources:{}", vfsDir.getAbsolutePath());
                    new LocalResourceStore("/", vfsDir).visitResource("/", new ITreeStateVisitor<>() {
                        @Override
                        public TreeVisitResult beginNodeState(ResourceTreeVisitState state) {
                            IResource res = state.getCurrent();
                            if (res.getName().startsWith("~"))
                                return TreeVisitResult.CONTINUE;

                            if (res.isDirectory()) {
                                store.addResourceIfAbsent(res);
                                return TreeVisitResult.CONTINUE;
                            }


                            boolean b = store.addResourceIfAbsent(res);
                            if (!b && !isAllowDuplicate(res.getPath()) && CFG_CHECK_DUPLICATE_VFS_RESOURCE.get()) {
                                throw new NopException(ERR_RESOURCE_DUPLICATE_VFS_RESOURCE)
                                        .param(ARG_PATH, res.getPath()).param(ARG_RESOURCE2, res)
                                        .param(ARG_RESOURCE1, store.getResource(res.getPath()));
                            }
                            return TreeVisitResult.CONTINUE;
                        }
                    });
                }
            }
        }
        return store;
    }

    protected IResource normalizeResource(IResource resource) {
        File file = resource.toFile();
        if (file != null) {
            IResource srcResource = toSrcResource(resource.getPath(), file);
            if (srcResource != null)
                return srcResource;
            if (!(resource instanceof FileResource)) {
                resource = new FileResource(resource.getPath(), file);
            }
        }
        return resource;
    }

    // 如果是target/classes目录下的资源，则尝试转换为src/main/resources目录下是否存在同名资源。
    protected IResource toSrcResource(String resourcePath, File file) {
        String path = FileHelper.getAbsolutePath(file);
        int pos = path.lastIndexOf("/target/classes/");
        if (pos > 0) {
            String srcPath = path.substring(0, pos) + "/src/main/resources/"
                    + path.substring(pos + "/target/classes/".length());
            FileResource srcResource = new FileResource(resourcePath, new File(srcPath));
            if (srcResource.exists())
                return srcResource;

            return null;
        }

        pos = path.lastIndexOf("/target/test-classes/");
        if (pos > 0) {
            String srcPath = path.substring(0, pos) + "/src/test/resources/"
                    + path.substring(pos + "/target/test-classes/".length());
            FileResource srcResource = new FileResource(resourcePath, new File(srcPath));
            if (srcResource.exists())
                return srcResource;
        }
        return null;
    }

    boolean isAllowDuplicate(String path) {
        return path.endsWith("/_module");
    }

    ZipFile newZipFile(File file) {
        try {
            ZipFile zipFile = new ZipFile(file);
            zipFiles.add(zipFile);
            return zipFile;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}