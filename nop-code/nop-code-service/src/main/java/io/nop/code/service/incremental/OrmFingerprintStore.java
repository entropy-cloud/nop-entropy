package io.nop.code.service.incremental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
import io.nop.code.core.util.DigestHelper;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
public class OrmFingerprintStore implements IFingerprintStore {

    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;
    private final Function<String, String> pathMapper;

    public OrmFingerprintStore(IDaoProvider daoProvider, IOrmTemplate ormTemplate) {
        this(daoProvider, ormTemplate, Function.identity());
    }

    public OrmFingerprintStore(IDaoProvider daoProvider, IOrmTemplate ormTemplate,
                               Function<String, String> pathMapper) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
        this.pathMapper = pathMapper != null ? pathMapper : Function.identity();
    }

    @Override
    public void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException {
        if (fingerprints == null || fingerprints.isEmpty()) return;

        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);

        Map<String, String> existingPathToId = loadFileIdMapByIndex(fileDao, indexId);

        for (FileFingerprint fp : fingerprints) {
            String canonicalPath = pathMapper.apply(fp.getFilePath());
            String entityId = DigestHelper.sha256Hex(
                    (indexId + ":" + canonicalPath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);

            NopCodeFile fileEntity;
            boolean isNew = false;
            String existingId = existingPathToId.get(canonicalPath);
            if (existingId != null) {
                fileEntity = fileDao.getEntityById(existingId);
            } else {
                io.nop.orm.IOrmEntity cached = ormTemplate.get(NopCodeFile.class.getName(), entityId);
                if (cached != null) {
                    fileEntity = (NopCodeFile) cached;
                } else {
                    fileEntity = (NopCodeFile) ormTemplate.newEntity(NopCodeFile.class.getName());
                    fileEntity.setId(entityId);
                    fileEntity.setIndexId(indexId);
                    fileEntity.setFilePath(canonicalPath);
                    isNew = true;
                }
            }

            fileEntity.setFileHash(fp.getContentHash());
            fileEntity.setLastModified(fp.getLastModified());
            fileEntity.setFileSize(fp.getFileSize());

            if (isNew) {
                fileDao.saveEntity(fileEntity);
                existingPathToId.put(canonicalPath, fileEntity.getId());
            }
        }
    }

    private Map<String, String> loadFileIdMapByIndex(IEntityDao<NopCodeFile> fileDao, String indexId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("id"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("filePath"));
        List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(query);
        Map<String, String> map = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object path = row.get("filePath");
            Object id = row.get("id");
            if (path != null && id != null) {
                map.put(path.toString(), id.toString());
            }
        }
        return map;
    }

    @Override
    public List<FileFingerprint> loadFingerprints(String indexId) throws IOException {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));

        List<NopCodeFile> entities = fileDao.findAllByQuery(query);
        // Full file list needed for fingerprint comparison
        List<FileFingerprint> fingerprints = new ArrayList<>(entities.size());

        for (NopCodeFile entity : entities) {
            FileFingerprint fp = new FileFingerprint();
            fp.setFilePath(pathMapper.apply(entity.getFilePath()));
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
        // Full file list needed for complete deletion
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
