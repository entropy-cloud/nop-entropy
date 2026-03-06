package io.nop.commons.path;

import java.util.Map;

public interface ICompiledPathMatcher {

    boolean match(String path);

    boolean matchStart(String path);

    Map<String, String> extractUriTemplateVariables(String path);
}
