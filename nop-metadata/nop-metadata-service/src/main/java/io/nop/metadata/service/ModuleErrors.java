package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface ModuleErrors extends NopMetadataArgs {

    ErrorCode ERR_MODULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.module-not-found",
                    "Module not found: {metaModuleId}", ARG_META_MODULE_ID);
    ErrorCode ERR_MODULE_NOT_DRAFTING =
            ErrorCode.define("nop.err.metadata.module-not-drafting",
                    "Module is not in drafting status: {status}", ARG_STATUS);
    ErrorCode ERR_MODULE_FULL_MODEL_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.module-full-model-not-found",
                    "Full ORM model (isDelta=false) not found for module, cannot generate manifest: {metaModuleId}",
                    ARG_META_MODULE_ID);
    ErrorCode ERR_ORM_RESOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.orm-resource-not-found",
                    "ORM resource not found: {path}", ARG_PATH);
    ErrorCode ERR_ORM_RESOURCE_READ_FAILED =
            ErrorCode.define("nop.err.metadata.orm-resource-read-failed",
                    "ORM resource read failed: {path} -- {error}", ARG_PATH, ARG_ERROR);
    ErrorCode ERR_MODEL_DELTA_PARSE_FAILED =
            ErrorCode.define("nop.err.metadata.module-delta-parse-failed",
                    "Delta model parse failed (x:extends present, delta=full fallback would lose delta "
                            + "overrides): {path} -- {error}", ARG_PATH, ARG_ERROR);
    ErrorCode ERR_MANIFEST_BUILD_FAILED =
            ErrorCode.define("nop.err.metadata.manifest-build-failed",
                    "MetaManifest build failed: {metaModuleId} -- {error}",
                    ARG_META_MODULE_ID, ARG_ERROR);
    ErrorCode ERR_MANIFEST_MODULE_NULL =
            ErrorCode.define("nop.err.metadata.manifest-module-null",
                    "MetaManifest build failed: module is null",
                    ARG_META_MODULE_ID);
    ErrorCode ERR_MANIFEST_ORM_MODEL_NULL =
            ErrorCode.define("nop.err.metadata.manifest-orm-model-null",
                    "MetaManifest build failed: full ORM model is null",
                    ARG_META_MODULE_ID);
}
