package io.nop.core.resource.store;

import java.util.List;
import java.util.zip.ZipFile;

public interface IDeltaResourceStoreBuilder {

    IDeltaResourceStore build(VfsConfig config);

    List<ZipFile> getZipFiles();
}
