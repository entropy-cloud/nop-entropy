/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

// copy from spring framework

import com.fasterxml.jackson.annotation.JsonCreator;
import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.lang.IClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.*;

import static io.nop.api.core.ApiErrors.ARG_CLASS_NAME;
import static io.nop.api.core.ApiErrors.ARG_EXPECTED_TYPE;
import static io.nop.commons.CommonErrors.*;


@NoReflection
public class ClassHelper {
    static final Logger LOG = LoggerFactory.getLogger(ClassHelper.class);

    /**
     * Suffix for array class names: "[]"
     */
    public static final String ARRAY_SUFFIX = "[]";

    /**
     * Prefix for internal array class names: "["
     */
    private static final String INTERNAL_ARRAY_PREFIX = "[";

    /**
     * Prefix for internal non-primitive array class names: "[L"
     */
    private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";

    /**
     * The path separator character: '/'
     */
    private static final char PATH_SEPARATOR = '/';

    /**
     * The package separator character '.'
     */
    private static final char PACKAGE_SEPARATOR = '.';

    /**
     * The inner class separator character '$'
     */
    private static final char INNER_CLASS_SEPARATOR = '$';

    /**
     * Map with primitive wrapper type as key and corresponding primitive type as value, for example: Integer.class ->
     * int.class.
     */
    private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new HashMap<Class<?>, Class<?>>(8);

    /**
     * Map with primitive type as key and corresponding wrapper type as value, for example: int.class -> Integer.class.
     */
    private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new HashMap<Class<?>, Class<?>>(8);

    /**
     * Map with primitive type name as key and corresponding primitive type as value, for example: "int" -> "int.class".
     */
    private static final Map<String, Class<?>> primitiveTypeNameMap = new HashMap<String, Class<?>>(32);

    /**
     * Map with common "java.lang" class name as key and corresponding Class as value. Primarily for efficient
     * deserialization of remote invocations.
     */
    private static final Map<String, Class<?>> commonClassCache = new HashMap<String, Class<?>>(32);

    static {
        primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
        primitiveWrapperTypeMap.put(Byte.class, byte.class);
        primitiveWrapperTypeMap.put(Character.class, char.class);
        primitiveWrapperTypeMap.put(Double.class, double.class);
        primitiveWrapperTypeMap.put(Float.class, float.class);
        primitiveWrapperTypeMap.put(Integer.class, int.class);
        primitiveWrapperTypeMap.put(Long.class, long.class);
        primitiveWrapperTypeMap.put(Short.class, short.class);

        for (Map.Entry<Class<?>, Class<?>> entry : primitiveWrapperTypeMap.entrySet()) {
            primitiveTypeToWrapperMap.put(entry.getValue(), entry.getKey());
            registerCommonClasses(entry.getKey());
        }

        Set<Class<?>> primitiveTypes = new HashSet<Class<?>>(32);
        primitiveTypes.addAll(primitiveWrapperTypeMap.values());
        primitiveTypes.addAll(Arrays.asList(new Class<?>[]{boolean[].class, byte[].class, char[].class,
                double[].class, float[].class, int[].class, long[].class, short[].class}));
        primitiveTypes.add(void.class);
        for (Class<?> primitiveType : primitiveTypes) {
            primitiveTypeNameMap.put(primitiveType.getName(), primitiveType);
        }

        registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class, Float[].class,
                Integer[].class, Long[].class, Short[].class);
        registerCommonClasses(Number.class, Number[].class, String.class, String[].class, Object.class, Object[].class,
                Class.class, Class[].class);
        registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class, Error.class,
                StackTraceElement.class, StackTraceElement[].class);
    }

    /**
     * Register the given common classes with the ClassUtils cache.
     */
    private static void registerCommonClasses(Class<?>... commonClasses) {
        for (Class<?> clazz : commonClasses) {
            commonClassCache.put(clazz.getName(), clazz);
        }
    }

    private static IClassLoader s_safeClassLoader = name -> forName(name);

    public static void registerSafeClassLoader(IClassLoader classLoader) {
        s_safeClassLoader = classLoader;
    }

    public static IClassLoader getSafeClassLoader() {
        return s_safeClassLoader;
    }

    /**
     * Return the default ClassLoader to use: typically the thread context ClassLoader, if available; the ClassLoader
     * that loaded the ClassUtils class will be used as fallback.
     * <p>
     * Call this method if you intend to use the thread context ClassLoader in a scenario where you clearly prefer a
     * non-null ClassLoader reference: for example, for class path resource loading (but not necessarily for
     * {@code Class.forName}, which accepts a {@code null} ClassLoader reference as well).
     *
     * @return the default ClassLoader (only {@code null} if even the system ClassLoader isn't accessible)
     * @see Thread#getContextClassLoader()
     * @see ClassLoader#getSystemClassLoader()
     */
    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) { //NOPMD - suppressed EmptyCatchBlock
            // Cannot access thread context ClassLoader - falling back...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = ClassHelper.class.getClassLoader();
            if (cl == null) {
                // getClassLoader() returning null indicates the bootstrap
                // ClassLoader
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable ex) { //NOPMD - suppressed EmptyCatchBlock
                    // Cannot access system ClassLoader - oh well, maybe the
                    // caller can live with null...
                }
            }
        }
        return cl;
    }

    /**
     * Override the thread context ClassLoader with the environment's bean ClassLoader if necessary, i.e. if the bean
     * ClassLoader is not equivalent to the thread context ClassLoader already.
     *
     * @param classLoaderToUse the actual ClassLoader to use for the thread context
     * @return the original thread context ClassLoader, or {@code null} if not overridden
     */
    public static ClassLoader overrideThreadContextClassLoader(ClassLoader classLoaderToUse) {
        if (classLoaderToUse == null)
            return null;

        Thread currentThread = Thread.currentThread();
        ClassLoader threadContextClassLoader = currentThread.getContextClassLoader();
        if (classLoaderToUse != null && !classLoaderToUse.equals(threadContextClassLoader)) {
            currentThread.setContextClassLoader(classLoaderToUse);
            return threadContextClassLoader;
        } else {
            return null;
        }
    }

    /**
     * Resolve the given class name as primitive class, if appropriate, according to the JVM's naming rules for
     * primitive classes.
     * <p>
     * Also supports the JVM's internal class names for primitive arrays. Does <i>not</i> support the "[]" suffix
     * notation for primitive arrays; this is only supported by {@link #forName(String, ClassLoader)}.
     *
     * @param name the name of the potentially primitive class
     * @return the primitive class, or {@code null} if the name does not denote a primitive class or primitive array
     * class
     */
    public static Class<?> resolvePrimitiveClassName(String name) {
        Class<?> result = null;
        // Most class names will be quite long, considering that they
        // SHOULD sit in a package, so a length check is worthwhile.
        if (name != null && name.length() <= 8) {
            // Could be a primitive - likely.
            result = primitiveTypeNameMap.get(name);
        }
        return result;
    }

    public static Object newInstance(String className, ClassLoader loader) {
        try {
            Class<?> clazz = forName(className, loader);
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new NopException(ERR_REFLECT_NEW_INSTANCE_FAIL, e).param(ARG_CLASS, className);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }

    }

    public static Object newInstance(Class<?> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new NopException(ERR_REFLECT_NEW_INSTANCE_FAIL, e).param(ARG_CLASS, clazz.getName());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static Constructor<?> getDefaultConstructor(Class<?> clazz) {
        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            if (constructors.length == 0)
                return null;

            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0)
                    return constructor;
            }

            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(JsonCreator.class))
                    return constructor;
            }

            return constructors[0];
        } catch (Exception e) {
            throw NopException.wrap(e);
        }
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        return forName(name, getDefaultClassLoader());
    }

    public static Class<?> safeLoadClass(String name) throws ClassNotFoundException {
        return getSafeClassLoader().loadClass(name);
    }

    /**
     * Replacement for {@code Class.forName()} that also returns Class instances for primitives (e.g. "int") and array
     * class names (e.g. "String[]"). Furthermore, it is also capable of resolving inner class names in Java source
     * style (e.g. "java.lang.Thread.State" instead of "java.lang.Thread$State").
     *
     * @param name        the name of the Class
     * @param classLoader the class loader to use (may be {@code null}, which indicates the default class loader)
     * @return Class instance for the supplied name
     * @throws ClassNotFoundException if the class was not found
     * @throws LinkageError           if the class file could not be loaded
     * @see Class#forName(String, boolean, ClassLoader)
     */
    public static Class<?> forName(String name, ClassLoader classLoader) throws ClassNotFoundException, LinkageError {
        Class<?> clazz = resolvePrimitiveClassName(name);
        if (clazz == null) {
            clazz = commonClassCache.get(name);
        }
        if (clazz != null) {
            return clazz;
        }

        // "java.lang.String[]" style arrays
        if (name.endsWith(ARRAY_SUFFIX)) {
            String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
            Class<?> elementClass = forName(elementClassName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }

        // "[Ljava.lang.String;" style arrays
        if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(";")) {
            String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
            Class<?> elementClass = forName(elementName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }

        // "[[I" or "[[Ljava.lang.String;" style arrays
        if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
            String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
            Class<?> elementClass = forName(elementName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }

        ClassLoader clToUse = classLoader;
        if (clToUse == null) {
            clToUse = getDefaultClassLoader();
        }
        try {
            return (clToUse != null ? clToUse.loadClass(name) : Class.forName(name));
        } catch (ClassNotFoundException ex) {
            LOG.debug("nop.class-not-found:class={},classLoader={}", name, clToUse);
            int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
            if (lastDotIndex != -1) {
                String innerClassName = name.substring(0, lastDotIndex) + INNER_CLASS_SEPARATOR
                        + name.substring(lastDotIndex + 1);
                try {
                    return (clToUse != null ? clToUse.loadClass(innerClassName) : Class.forName(innerClassName));
                } catch (ClassNotFoundException ex2) { //NOPMD - suppressed EmptyCatchBlock
                    // Swallow - let original exception get through
                }
            }
            if (LOG.isTraceEnabled())
                LOG.trace("nop.err.commons.lang.load-class-fail:name={},class_path={}", name,
                        ClassHelper.getClassPathURLs(classLoader));
            throw ex;
        }
    }

    /**
     * Determine the common ancestor of the given classes, if any.
     *
     * @param clazz1 the class to introspect
     * @param clazz2 the other class to introspect
     * @return the common ancestor (i.e. common superclass, one interface extending the other), or {@code null} if none
     * found. If any of the given classes is {@code null}, the other class will be returned.
     * @since 3.2.6
     */
    public static Class<?> determineCommonAncestor(Class<?> clazz1, Class<?> clazz2) {
        if (clazz1 == null) {
            return clazz2;
        }
        if (clazz2 == null) {
            return clazz1;
        }
        if (clazz1.isAssignableFrom(clazz2)) {
            return clazz1;
        }
        if (clazz2.isAssignableFrom(clazz1)) {
            return clazz2;
        }
        Class<?> ancestor = clazz1;
        do {
            ancestor = ancestor.getSuperclass();
            if (ancestor == null || Object.class.equals(ancestor)) {
                return null;
            }
        } while (!ancestor.isAssignableFrom(clazz2));
        return ancestor;
    }

    /**
     * Convert a "/"-based resource path to a "."-based fully qualified class name.
     *
     * @param resourcePath the resource path pointing to a class
     * @return the corresponding fully qualified class name
     */
    public static String convertResourcePathToClassName(String resourcePath) {
        Guard.notNull(resourcePath, "Resource path must not be null");
        return resourcePath.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
    }

    /**
     * Convert a "."-based fully qualified class name to a "/"-based resource path.
     *
     * @param className the fully qualified class name
     * @return the corresponding resource path, pointing to the class
     */
    public static String convertClassNameToResourcePath(String className) {
        Guard.notNull(className, "Class name must not be null");
        return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
    }

    /**
     * Return a path suitable for use with {@code ClassLoader.getResource} (also suitable for use with
     * {@code Class.getResource} by prepending a slash ('/') to the return value). Built by taking the package of the
     * specified class file, converting all dots ('.') to slashes ('/'), adding a trailing slash if necessary, and
     * concatenating the specified resource name to this. <br/>
     * As such, this function may be used to build a path suitable for loading a resource file that is in the same
     * package as a class file, although {link org.springframework.core.io.ClassPathResource} is usually even more
     * convenient.
     *
     * @param clazz        the Class whose package will be used as the base
     * @param resourceName the resource name to append. A leading slash is optional.
     * @return the built-up resource path
     * @see ClassLoader#getResource
     * @see Class#getResource
     */
    public static String addResourcePathToPackagePath(Class<?> clazz, String resourceName) {
        Guard.notNull(resourceName, "Resource name must not be null");
        if (!resourceName.startsWith("/")) {
            return classPackageAsResourcePath(clazz) + '/' + resourceName;
        }
        return classPackageAsResourcePath(clazz) + resourceName;
    }

    /**
     * Given an input class object, return a string which consists of the class's package name as a pathname, i.e., all
     * dots ('.') are replaced by slashes ('/'). Neither a leading nor trailing slash is added. The result could be
     * concatenated with a slash and the name of a resource and fed directly to {@code ClassLoader.getResource()}. For
     * it to be fed to {@code Class.getResource} instead, a leading slash would also have to be prepended to the
     * returned value.
     *
     * @param clazz the input class. A {@code null} value or the default (empty) package will result in an empty string
     *              ("") being returned.
     * @return a path which represents the package name
     * @see ClassLoader#getResource
     * @see Class#getResource
     */
    public static String classPackageAsResourcePath(Class<?> clazz) {
        if (clazz == null) {
            return "";
        }
        String className = clazz.getName();
        int packageEndIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
        if (packageEndIndex == -1) {
            return "";
        }
        String packageName = className.substring(0, packageEndIndex);
        return packageName.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
    }

    /**
     * Return all interfaces that the given instance implements as an array, including ones implemented by superclasses.
     *
     * @param instance the instance to analyze for interfaces
     * @return all interfaces that the given instance implements as an array
     */
    public static Class<?>[] getAllInterfaces(Object instance) {
        Guard.notNull(instance, "Instance must not be null");
        return getAllInterfacesForClass(instance.getClass());
    }

    /**
     * Return all interfaces that the given class implements as an array, including ones implemented by superclasses.
     * <p>
     * If the class itself is an interface, it gets returned as sole interface.
     *
     * @param clazz the class to analyze for interfaces
     * @return all interfaces that the given object implements as an array
     */
    public static Class<?>[] getAllInterfacesForClass(Class<?> clazz) {
        return getAllInterfacesForClass(clazz, null);
    }

    /**
     * Return all interfaces that the given class implements as an array, including ones implemented by superclasses.
     * <p>
     * If the class itself is an interface, it gets returned as sole interface.
     *
     * @param clazz       the class to analyze for interfaces
     * @param classLoader the ClassLoader that the interfaces need to be visible in (may be {@code null} when accepting all
     *                    declared interfaces)
     * @return all interfaces that the given object implements as an array
     */
    public static Class<?>[] getAllInterfacesForClass(Class<?> clazz, ClassLoader classLoader) {
        Set<Class<?>> ifcs = getAllInterfacesForClassAsSet(clazz, classLoader);
        return ifcs.toArray(new Class<?>[ifcs.size()]);
    }

    /**
     * Return all interfaces that the given instance implements as a Set, including ones implemented by superclasses.
     *
     * @param instance the instance to analyze for interfaces
     * @return all interfaces that the given instance implements as a Set
     */
    public static Set<Class<?>> getAllInterfacesAsSet(Object instance) {
        Guard.notNull(instance, "Instance must not be null");
        return getAllInterfacesForClassAsSet(instance.getClass());
    }

    /**
     * Return all interfaces that the given class implements as a Set, including ones implemented by superclasses.
     * <p>
     * If the class itself is an interface, it gets returned as sole interface.
     *
     * @param clazz the class to analyze for interfaces
     * @return all interfaces that the given object implements as a Set
     */
    public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz) {
        return getAllInterfacesForClassAsSet(clazz, null);
    }

    /**
     * Return all interfaces that the given class implements as a Set, including ones implemented by superclasses.
     * <p>
     * If the class itself is an interface, it gets returned as sole interface.
     *
     * @param clazz       the class to analyze for interfaces
     * @param classLoader the ClassLoader that the interfaces need to be visible in (may be {@code null} when accepting all
     *                    declared interfaces)
     * @return all interfaces that the given object implements as a Set
     */
    public static Set<Class<?>> getAllInterfacesForClassAsSet(Class<?> clazz, ClassLoader classLoader) {
        Guard.notNull(clazz, "Class must not be null");
        if (clazz.isInterface() && isVisible(clazz, classLoader)) {
            return Collections.<Class<?>>singleton(clazz);
        }
        Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
        while (clazz != null) {
            Class<?>[] ifcs = clazz.getInterfaces();
            for (Class<?> ifc : ifcs) {
                interfaces.addAll(getAllInterfacesForClassAsSet(ifc, classLoader));
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    /**
     * Check whether the given class is visible in the given ClassLoader.
     *
     * @param clazz       the class to check (typically an interface)
     * @param classLoader the ClassLoader to check against (may be {@code null}, in which case this method will always return
     *                    {@code true})
     */
    public static boolean isVisible(Class<?> clazz, ClassLoader classLoader) {
        if (classLoader == null) {
            return true;
        }
        try {
            Class<?> actualClass = classLoader.loadClass(clazz.getName());
            return (clazz == actualClass);
            // Else: different interface class found...
        } catch (ClassNotFoundException ex) {
            // No interface class found...
            return false;
        }
    }

    public static List<URL> getClassPathURLs(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader) classLoader;
            return Arrays.asList(ucl.getURLs());
        }
        return Collections.emptyList();
    }

    public static List<URL> getClassPathURLs() {
        return getClassPathURLs(getDefaultClassLoader());
    }

    public static String findContainingJar(Class<?> clazz) {
        return findContainingJar(clazz, null);
    }

    // Copy From Apache Kylin

    /**
     * Load the first jar library contains clazz with preferJarKeyword matched. If preferJarKeyword is null, just load
     * the jar likes Hadoop Commons' ClassUtil
     *
     * @param clazz
     * @param preferJarKeyWord
     * @return
     */
    public static String findContainingJar(Class<?> clazz, String preferJarKeyWord) {
        ClassLoader loader = clazz.getClassLoader();
        String classFile = clazz.getName().replaceAll("\\.", "/") + ".class";

        try {
            Enumeration<URL> e = loader.getResources(classFile);

            URL url = null;
            do {
                if (!e.hasMoreElements()) {
                    if (url == null)
                        return null;
                    else
                        break;
                }

                url = e.nextElement();
                if (!"jar".equals(url.getProtocol()))
                    break;
                if (preferJarKeyWord != null && url.getPath().indexOf(preferJarKeyWord) != -1)
                    break;
                if (preferJarKeyWord == null)
                    break;
            } while (true);

            String toReturn = url.getPath();
            if (toReturn.startsWith("file:")) {
                toReturn = toReturn.substring("file:".length());
            }

            toReturn = URLDecoder.decode(toReturn, "UTF-8");
            return toReturn.replaceAll("!.*$", "");
        } catch (IOException var6) {
            throw new RuntimeException(var6);
        }
    }

    /**
     * Generates a simplified name from a {@link Class}. Similar to {@link Class#getSimpleName()}, but it works fine
     * with anonymous classes.
     */
    public static String simpleClassName(Class<?> clazz) {
        String className = Guard.notNull(clazz, "reflect.err_null_class").getName();
        final int lastDotIdx = className.lastIndexOf('.');
        if (lastDotIdx > -1) {
            return className.substring(lastDotIdx + 1);
        }
        return className;
    }

    public static String getCallLocationName() {
        return getCallLocationName(4);
    }

    public static String getCallLocationName(int depth) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        if (stackTrace.length <= depth) {
            return "<unknown>";
        }

        StackTraceElement elem = stackTrace[depth];

        return elem.getMethodName() + "(" + elem.getFileName() + ":" + elem.getLineNumber() + ")";
    }

    public static Collection<Object> newCollection(Class clazz) {
        if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))
            return (Collection<Object>) ClassHelper.newInstance(clazz);

        if (clazz == SortedSet.class)
            return new TreeSet<>();
        if (clazz == Set.class)
            return new LinkedHashSet<>();
        if (clazz == Set.class)
            return new LinkedHashSet<>();
        return new ArrayList<>();
    }

    public static Map<String, Object> newMap(Class clazz) {
        if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))
            return (Map<String, Object>) ClassHelper.newInstance(clazz);

        if (clazz == SortedMap.class)
            return new TreeMap<>();
        return new LinkedHashMap<>();
    }

    public static Object newInstance(Constructor<?> constructor, Object[] args) {
        try {
            return constructor.newInstance(args);
        } catch (IllegalArgumentException e) {
            LOG.info("nop.commons.reflect.new-instance-fail:constructor={},args={}", constructor, Arrays.asList(args),
                    e);
            throw NopException.adapt(e);
        } catch (InstantiationException e) {
            throw new NopException(ERR_REFLECT_NEW_INSTANCE_FAIL, e).param(ARG_CLASS,
                    constructor.getDeclaringClass().getName());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static Object invoke(Method method, Object obj, Object[] args) {
        try {
            return method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            LOG.info("nop.commons.reflect.invoke-method-fail:obj={},class={},method={},args={}", obj,
                    obj == null ? null : obj.getClass(), method, Arrays.asList(args), e.getTargetException());
            throw NopException.adapt(e.getTargetException());
        } catch (Exception e2) {
            LOG.error("nop.commons.reflect.invoke-method-fail:obj={},class={},method={},args={}", obj,
                    obj == null ? null : obj.getClass(), method, Arrays.asList(args), e2);
            throw NopException.adapt(e2);
        }
    }

    public static Class<?> getObjClass(Object o) {
        if (o == null)
            return null;
        if (o instanceof Annotation)
            return ((Annotation) o).annotationType();
        return o.getClass();
    }

    public static Class<?> loadClass(String className, Class<?> targetClass) {
        if (StringHelper.isEmpty(className))
            return null;

        Class<?> clazz;
        try {
            clazz = ClassHelper.getSafeClassLoader().loadClass(className);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        if (!targetClass.isAssignableFrom(clazz))
            throw new NopException(ERR_LOAD_CLASS_NOT_EXPECTED_TYPE).param(ARG_CLASS_NAME, className)
                    .param(ARG_EXPECTED_TYPE, targetClass);
        return clazz;
    }

    public static String getCanonicalClassName(Class<?> clazz) {
        return clazz == null ? null : clazz.getCanonicalName();
    }

    private static Class<? extends Annotation> _vertxDataObject = null;

    public static boolean isVertxDataObject(Class<?> clazz) {
        if (_vertxDataObject == null) {
            Class<? extends Annotation> dataObject = null;
            try {
                dataObject = (Class<? extends Annotation>) getSafeClassLoader().loadClass("io.vertx.codegen.annotations.DataObject");
            } catch (Exception e) {
                LOG.trace("nop.commons.not-find-vertx-data-object");
            }
            _vertxDataObject = dataObject;
            if (dataObject == null) {
                _vertxDataObject = DataBean.class;
            }
        }
        if (_vertxDataObject != DataBean.class) {
            return clazz.getAnnotation(_vertxDataObject) != null;
        } else {
            return false;
        }
    }

    public static boolean isDataBean(Class<?> clazz) {
        return clazz.isAnnotationPresent(DataBean.class) || isVertxDataObject(clazz);
    }
}
