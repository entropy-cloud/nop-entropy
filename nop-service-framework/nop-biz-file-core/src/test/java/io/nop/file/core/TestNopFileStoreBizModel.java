package io.nop.file.core;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 上传扩展名白名单的大小写归一：配置 "jpg" 时上传 "a.JPG" 不得误拒。
 */
class TestNopFileStoreBizModel {

    private NopFileStoreBizModel modelWithAllowedExts(String... exts) {
        NopFileStoreBizModel model = new NopFileStoreBizModel();
        model.setAllowedFileExts(Set.of(exts));
        return model;
    }

    @Test
    void checkFileExt_caseInsensitiveMatch() {
        NopFileStoreBizModel model = modelWithAllowedExts("jpg", "PNG");

        // 同名不同大小写必须放行（fileExt 取自上传文件名原始大小写）
        assertDoesNotThrow(() -> model.checkFileExt("JPG"), "配置 jpg 时上传 a.JPG 不得误拒");
        assertDoesNotThrow(() -> model.checkFileExt("jpg"));
        assertDoesNotThrow(() -> model.checkFileExt("png"));
        assertDoesNotThrow(() -> model.checkFileExt("PnG"));

        // 白名单之外的仍拒绝
        assertThrows(NopException.class, () -> model.checkFileExt("exe"));
    }

    @Test
    void checkFileExt_emptyConfig_noRestriction() {
        NopFileStoreBizModel model = modelWithAllowedExts();
        // 空配置 = 不限制（既有行为，契约变更需产品决策，见审计标注）
        assertDoesNotThrow(() -> model.checkFileExt("html"));
    }
}
