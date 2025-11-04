package io.nop.core.lang.utils;

import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@GlobalInstance
public class CodeLangMap {
    static final CodeLangMap _instance = new CodeLangMap();

    private final Map<String, String> fileExtToCodeLangMap = new HashMap<>();
    private final Map<String, String> codeLangToFileExtMap = new HashMap<>();

    protected CodeLangMap() {
        initializeMappings();
    }

    public static CodeLangMap instance() {
        return _instance;
    }

    private void initializeMappings() {
        // 编程语言到文件扩展名的映射
        addMapping("java", ".java");
        addMapping("python", ".py");
        addMapping("javascript", ".js");
        addMapping("typescript", ".ts");
        addMapping("c", ".c");
        addMapping("cpp", ".cpp");
        addMapping("csharp", ".cs");
        addMapping("go", ".go");
        addMapping("ruby", ".rb");
        addMapping("php", ".php");
        addMapping("swift", ".swift");
        addMapping("kotlin", ".kt");
        addMapping("scala", ".scala");
        addMapping("rust", ".rs");
        addMapping("dart", ".dart");
        addMapping("r", ".r");
        addMapping("perl", ".pl");
        addMapping("lua", ".lua");
        addMapping("haskell", ".hs");
        addMapping("elixir", ".ex");
        addMapping("clojure", ".clj");
        addMapping("bash", ".sh");
        addMapping("powershell", ".ps1");
        addMapping("html", ".html");
        addMapping("css", ".css");
        addMapping("sql", ".sql");
        addMapping("xml", ".xml");
        addMapping("json", ".json");
        addMapping("yaml", ".yaml");
        fileExtToCodeLangMap.put(".yml", "yaml");
        addMapping("markdown", ".md");
        addMapping("text", ".txt");
    }

    /**
     * 添加语言和文件扩展名的映射
     *
     * @param language  编程语言
     * @param extension 文件扩展名
     */
    public void addMapping(String language, String extension) {
        codeLangToFileExtMap.put(language, extension);
        fileExtToCodeLangMap.put(extension, language);
    }

    /**
     * 根据文件扩展名获取编程语言
     *
     * @param extension 文件扩展名
     * @return 编程语言，如果未找到则返回null
     */
    public String getLanguageFromExtension(String extension) {
        return fileExtToCodeLangMap.get(extension.toLowerCase());
    }

    /**
     * 根据编程语言获取文件扩展名
     *
     * @param language 编程语言
     * @return 文件扩展名，如果未找到则返回null
     */
    public String getExtensionFromLanguage(String language) {
        return codeLangToFileExtMap.get(language.toLowerCase());
    }

    /**
     * 检查是否包含指定的文件扩展名
     *
     * @param extension 文件扩展名
     * @return 如果包含则返回true，否则返回false
     */
    public boolean containsExtension(String extension) {
        return fileExtToCodeLangMap.containsKey(extension.toLowerCase());
    }

    /**
     * 检查是否包含指定的编程语言
     *
     * @param language 编程语言
     * @return 如果包含则返回true，否则返回false
     */
    public boolean containsLanguage(String language) {
        return codeLangToFileExtMap.containsKey(language.toLowerCase());
    }

    /**
     * 获取所有支持的文件扩展名
     *
     * @return 文件扩展名集合
     */
    public Set<String> getAllExtensions() {
        return new TreeSet<>(fileExtToCodeLangMap.keySet());
    }

    /**
     * 获取所有支持的编程语言
     *
     * @return 编程语言集合
     */
    public Set<String> getAllLanguages() {
        return new TreeSet<>(codeLangToFileExtMap.keySet());
    }
}