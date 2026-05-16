package io.nop.code.service.incremental;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OrmFingerprintStore implements IFingerprintStore {

    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;

    public OrmFingerprintStore(IDaoProvider daoProvider, IOrmTemplate ormTemplate) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
    }

    @Override
    public void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException {
        if (fingerprints == null || fingerprints.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        for (FileFingerprint fp : fingerprints) {
            String entityId = indexId + "_" + Math.abs(fp.getFilePath().hashCode());
            NopCodeFile existing = findByIndexAndPath(fileDao, indexId, fp.getFilePath());

            NopCodeFile fileEntity;
            boolean isNew = false;
            if (existing != null) {
                fileEntity = existing;
            } else {
                io.nop.orm.IOrmEntity cached = ormTemplate.get(NopCodeFile.class.getName(), entityId);
                if (cached != null) {
                    fileEntity = (NopCodeFile) cached;
                } else {
                    fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
                    fileEntity.setId(entityId);
                    fileEntity.setIndexId(indexId);
                    fileEntity.setFilePath(fp.getFilePath());
                    isNew = true;
                }
            }

            fileEntity.setFileHash(fp.getContentHash());
            fileEntity.setLastModified(fp.getLastModified());
            fileEntity.setFileSize(fp.getFileSize());

            if (isNew) {
                fileDao.saveEntity(fileEntity);
            }
        }
    }

    @Override
    public List<FileFingerprint> loadFingerprints(String indexId) throws IOException {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeFile> entities = fileDao.findAllByQuery(query);
        List<FileFingerprint> fingerprints = new ArrayList<>(entities.size());

        for (NopCodeFile entity : entities) {
            FileFingerprint fp = new FileFingerprint();
            fp.setFilePath(entity.getFilePath());
            fp.setContentHash(entity.getFileHash());
            fp.setLastModified(entity.getLastModified() != null ? entity.getLastModified() : 0L);
            fp.setFileSize(entity.getFileSize() != null ? entity.getFileSize() : 0L);
            fingerprints.add(fp);
        }

        return fingerprints;
    }

    @Override
    public void deleteByPaths(String indexId, List<String> filePaths) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        for (String path : filePaths) {
            NopCodeFile existing = findByIndexAndPath(fileDao, indexId, path);
            if (existing != null) {
                fileDao.deleteEntity(existing);
            }
        }
    }

    @Override
    public void deleteByIndex(String indexId) throws IOException {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeFile> entities = fileDao.findAllByQuery(query);
        for (NopCodeFile entity : entities) {
            fileDao.deleteEntity(entity);
        }
    }

    private NopCodeFile findByIndexAndPath(IEntityDao<NopCodeFile> fileDao, String indexId, String filePath) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        List<NopCodeFile> results = fileDao.findAllByQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
}
