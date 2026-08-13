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

    private static final int BATCH_SIZE = 1000;

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
        Map<String, String> map = new HashMap<>();
        long offset = 0;
        while (true) {
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(query);
            for (Map<String, Object> row : rows) {
                Object path = row.get("filePath");
                Object id = row.get("id");
                if (path != null && id != null) {
                    map.put(path.toString(), id.toString());
                }
            }
            if (rows.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }
        return map;
    }

    @Override
    public List<FileFingerprint> loadFingerprints(String indexId) throws IOException {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("filePath"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("fileHash"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("lastModified"));
        query.addField(io.nop.api.core.beans.query.QueryFieldBean.forField("fileSize"));

        // Projection (avoid CLOB sourceCode load) + paginated to exhaust all rows
        List<FileFingerprint> fingerprints = new ArrayList<>();
        long offset = 0;
        while (true) {
            query.setOffset(offset);
            query.setLimit(BATCH_SIZE);
            List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(query);
            for (Map<String, Object> row : rows) {
                FileFingerprint fp = new FileFingerprint();
                Object path = row.get("filePath");
                fp.setFilePath(pathMapper.apply(path != null ? path.toString() : ""));
                Object hash = row.get("fileHash");
                fp.setContentHash(hash != null ? hash.toString() : null);
                Object lm = row.get("lastModified");
                fp.setLastModified(lm != null ? ((Number) lm).longValue() : 0L);
                Object sz = row.get("fileSize");
                fp.setFileSize(sz != null ? ((Number) sz).longValue() : 0L);
                fingerprints.add(fp);
            }
            if (rows.size() < BATCH_SIZE) break;
            offset += BATCH_SIZE;
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
        // Filter-based delete: exhausts all matching rows in pages without loading full entities.
        // Loops until deleteByQuery affects zero rows (complete exhaustion, no silent truncation).
        while (true) {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("indexId", indexId));
            query.setLimit(BATCH_SIZE);
            long deleted = fileDao.deleteByQuery(query);
            if (deleted == 0) break;
        }
    }

    private NopCodeFile findByIndexAndPath(IEntityDao<NopCodeFile> fileDao, String indexId, String filePath) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("indexId", indexId));
        query.addFilter(FilterBeans.eq("filePath", filePath));
        query.setLimit(1);
        List<NopCodeFile> results = fileDao.findAllByQuery(query);
        return results.isEmpty() ? null : results.get(0);
    }
}
