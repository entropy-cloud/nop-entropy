package io.nop.ai.code_analyzer.stats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 扩展名到编程语言的全局映射器
 */
public class ExtensionLanguageMapper {
    private static final Map<String, String> GLOBAL_EXTENSION_TO_LANGUAGE = new HashMap<>();

    static {
        initializeGlobalMapping();
    }

    private static void initializeGlobalMapping() {
        // Java生态
        GLOBAL_EXTENSION_TO_LANGUAGE.put("java", "Java");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("kt", "Kotlin");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("scala", "Scala");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("groovy", "Groovy");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("clj", "Clojure");

        // C/C++
        GLOBAL_EXTENSION_TO_LANGUAGE.put("c", "C");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("h", "C");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cpp", "C++");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cxx", "C++");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cc", "C++");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("hpp", "C++");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("hxx", "C++");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("hh", "C++");

        // JavaScript/TypeScript生态
        GLOBAL_EXTENSION_TO_LANGUAGE.put("js", "JavaScript");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("mjs", "JavaScript");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cjs", "JavaScript");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("ts", "TypeScript");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("jsx", "JSX");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("tsx", "TSX");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("vue", "Vue");

        // Web技术
        GLOBAL_EXTENSION_TO_LANGUAGE.put("html", "HTML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("htm", "HTML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xhtml", "HTML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("css", "CSS");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("scss", "SCSS");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("sass", "Sass");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("less", "Less");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("styl", "Stylus");

        // Python
        GLOBAL_EXTENSION_TO_LANGUAGE.put("py", "Python");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pyx", "Python");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pyw", "Python");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pyi", "Python");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("py3", "Python");

        // 现代语言
        GLOBAL_EXTENSION_TO_LANGUAGE.put("go", "Go");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("rs", "Rust");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("swift", "Swift");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("dart", "Dart");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("zig", "Zig");

        // 传统语言
        GLOBAL_EXTENSION_TO_LANGUAGE.put("php", "PHP");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("rb", "Ruby");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cs", "C#");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("fs", "F#");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("vb", "Visual Basic");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("m", "Objective-C");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("mm", "Objective-C++");

        // 函数式语言
        GLOBAL_EXTENSION_TO_LANGUAGE.put("hs", "Haskell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("elm", "Elm");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("ml", "OCaml");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("f", "Fortran");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("f90", "Fortran");

        // 脚本语言
        GLOBAL_EXTENSION_TO_LANGUAGE.put("sh", "Shell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("bash", "Shell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("zsh", "Shell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("fish", "Shell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("ps1", "PowerShell");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("bat", "Batch");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cmd", "Batch");

        // 数据和配置
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xml", "XML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("json", "JSON");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("json5", "JSON");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("yaml", "YAML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("yml", "YAML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("toml", "TOML");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("ini", "INI");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("properties", "Properties");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("cfg", "Config");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("conf", "Config");

        // 数据库
        GLOBAL_EXTENSION_TO_LANGUAGE.put("sql", "SQL");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("plsql", "PL/SQL");

        // 文档和标记
        GLOBAL_EXTENSION_TO_LANGUAGE.put("md", "Markdown");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("markdown", "Markdown");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("rst", "reStructuredText");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("txt", "Text");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("tex", "LaTeX");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("adoc", "AsciiDoc");

        // 其他
        GLOBAL_EXTENSION_TO_LANGUAGE.put("r", "R");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("R", "R");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("jl", "Julia");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("lua", "Lua");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pl", "Perl");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pm", "Perl");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("tcl", "Tcl");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("pas", "Pascal");

        GLOBAL_EXTENSION_TO_LANGUAGE.put("xpl", "xlang");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xbiz", "xlang");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xmeta", "xlang");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xdef", "xlang");
        GLOBAL_EXTENSION_TO_LANGUAGE.put("xgen", "xlang");
    }

    /**
     * 根据扩展名获取编程语言
     */
    public static String getLanguage(String extension) {
        return GLOBAL_EXTENSION_TO_LANGUAGE.get(extension.toLowerCase());
    }

    /**
     * 获取所有支持的扩展名
     */
    public static Set<String> getSupportedExtensions() {
        return new HashSet<>(GLOBAL_EXTENSION_TO_LANGUAGE.keySet());
    }

    /**
     * 获取所有支持的语言
     */
    public static Set<String> getSupportedLanguages() {
        return new HashSet<>(GLOBAL_EXTENSION_TO_LANGUAGE.values());
    }

    /**
     * 添加自定义映射
     */
    public static void addCustomMapping(String extension, String language) {
        GLOBAL_EXTENSION_TO_LANGUAGE.put(extension.toLowerCase(), language);
    }

    /**
     * 批量添加自定义映射
     */
    public static void addCustomMappings(Map<String, String> customMappings) {
        customMappings.forEach((ext, lang) ->
                GLOBAL_EXTENSION_TO_LANGUAGE.put(ext.toLowerCase(), lang));
    }
}