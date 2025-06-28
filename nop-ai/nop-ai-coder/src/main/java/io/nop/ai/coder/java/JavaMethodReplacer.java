package io.nop.ai.coder.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaMethodReplacer {

    public static void applyMethodDiff(String filePath, String methodSignature, String newMethodBody) throws IOException {
        // 读取文件内容
        Path path = Paths.get(filePath);
        String content = new String(Files.readAllBytes(path));

        // 构建正则表达式匹配方法签名和左大括号
        String patternStr = buildPattern(methodSignature);
        Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        // 查找方法签名
        if (!matcher.find()) {
            throw new IllegalArgumentException("Method signature not found: " + methodSignature);
        }

        int start = matcher.start();
        int braceStart = matcher.end() - 1; // '{'的位置

        // 查找方法体结束位置（匹配的右大括号）
        int braceEnd = findMatchingBrace(content, braceStart);
        if (braceEnd == -1) {
            throw new IllegalArgumentException("Unbalanced braces in method body");
        }

        // 替换方法体（保留签名，只替换大括号内的内容）
        String newContent = content.substring(0, braceStart) 
                           + newMethodBody 
                           + content.substring(braceEnd + 1);

        // 写回文件
        Files.write(path, newContent.getBytes());
    }

    private static String buildPattern(String signature) {
        // 转义特殊字符，并将空格替换为\s+以匹配任意空白
        return Pattern.quote(signature)
                     .replace(" ", "\\E\\s+\\Q")  // 处理空格
                     .replace("\\Q\\E", "")         // 清理空引号
                     + "\\s*\\{";                   // 匹配左大括号
    }

    private static int findMatchingBrace(String content, int start) {
        int count = 1;  // 初始计数（已有一个左大括号）
        int index = start + 1;
        int length = content.length();
        
        // 状态跟踪
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        while (index < length && count > 0) {
            char c = content.charAt(index);
            char prev = (index > 0) ? content.charAt(index - 1) : 0;

            // 处理字符串和注释状态
            if (!inLineComment && !inBlockComment) {
                if (c == '\'' && !inDoubleQuote) {
                    if (!inSingleQuote || prev != '\\') {
                        inSingleQuote = !inSingleQuote;
                    }
                } else if (c == '"' && !inSingleQuote) {
                    if (!inDoubleQuote || prev != '\\') {
                        inDoubleQuote = !inDoubleQuote;
                    }
                } else if (c == '/' && index + 1 < length) {
                    char next = content.charAt(index + 1);
                    if (!inSingleQuote && !inDoubleQuote) {
                        if (next == '/') inLineComment = true;
                        if (next == '*') inBlockComment = true;
                    }
                }
            } else {
                if (inLineComment && c == '\n') {
                    inLineComment = false;
                } else if (inBlockComment && c == '*' && index + 1 < length) {
                    if (content.charAt(index + 1) == '/') {
                        inBlockComment = false;
                        index++; // 跳过'/'
                    }
                }
            }

            // 仅当不在字符串/注释中时处理大括号
            if (!inSingleQuote && !inDoubleQuote && !inLineComment && !inBlockComment) {
                if (c == '{') count++;
                if (c == '}') count--;
            }

            index++;
        }

        return (count == 0) ? index - 1 : -1; // 返回右大括号位置
    }

    // 示例用法
    public static void main(String[] args) throws IOException {
        String file = "Test.java";
        String signature = "public void example()";
        String newBody = "{\n    System.out.println(\"New implementation!\");\n}";
        
        applyMethodDiff(file, signature, newBody);
        System.out.println("Method replaced successfully!");
    }
}