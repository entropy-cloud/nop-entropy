package io.nop.ai.coder.file;

import java.util.List;

public interface IFileStore {
    List<FileContent> readFiles(List<String> paths);

    void writeFiles(List<FileContent> fileContents);

    FileContent readFile(String path);

    void writeFile(FileContent fileContent);

    boolean existsFile(String path);
}
