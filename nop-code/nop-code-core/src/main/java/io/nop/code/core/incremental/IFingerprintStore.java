package io.nop.code.core.incremental;

import java.io.IOException;
import java.util.List;

/**
 * 指纹存储抽象接口，将指纹持久化与具体实现解耦。
 */
public interface IFingerprintStore {
    /**
     * 保存指纹列表，覆盖该 indexId 下已有数据
     */
    void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException;

    /**
     * 加载指定 indexId 的指纹列表，不存在时返回空列表
     */
    List<FileFingerprint> loadFingerprints(String indexId) throws IOException;

    /**
     * 按 filePath 列表删除指纹，不存在的路径静默跳过
     */
    void deleteByPaths(String indexId, List<String> filePaths) throws IOException;

    /**
     * 删除整个 indexId 的全部指纹
     */
    void deleteByIndex(String indexId) throws IOException;
}
