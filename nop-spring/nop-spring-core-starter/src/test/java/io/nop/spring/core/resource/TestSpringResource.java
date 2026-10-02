package io.nop.spring.core.resource;

import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: SpringResource adapts a Nop IResource to the
 * Spring Resource contract — the filename/description/content faces and the
 * createRelative branching (IFile delegates, non-file resources must refuse
 * with an explicit error instead of guessing a sibling path).
 */
public class TestSpringResource {

    @Test
    public void testWrapsInMemoryResourceFaces() throws Exception {
        InMemoryTextResource nopResource = new InMemoryTextResource("/reports/hello.txt", "hello");
        SpringResource resource = new SpringResource(nopResource);

        assertTrue(resource.exists());
        assertEquals("hello.txt", resource.getFilename());
        assertEquals("/reports/hello.txt", resource.getDescription());
        assertEquals(5, resource.contentLength(), "contentLength is the UTF-8 byte length");
        assertEquals("hello", new String(resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    @Test
    public void testNonExistentResourceReportsNotExists() {
        InMemoryTextResource missing = new InMemoryTextResource("/missing.txt", null);
        SpringResource resource = new SpringResource(missing);
        assertFalse(resource.exists());
    }

    @Test
    public void testCreateRelativeDelegatesForFileResources(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        File baseDir = Files.createDirectory(dir.resolve("sub")).toFile();
        FileResource nopFile = new FileResource(baseDir);
        SpringResource resource = new SpringResource(nopFile);

        Resource relative = resource.createRelative("nested.txt");
        assertTrue(relative instanceof SpringResource, "createRelative re-wraps the resolved Nop resource");
        assertEquals("nested.txt", relative.getFilename());
        assertTrue(relative.getDescription().endsWith("nested.txt"),
                "description carries the resolved Nop resource path");
    }

    @Test
    public void testCreateRelativeRefusesNonFileResources() {
        SpringResource resource = new SpringResource(new InMemoryTextResource("/in-memory.txt", "x"));
        assertThrows(IllegalArgumentException.class, () -> resource.createRelative("sibling.txt"),
                "only IFile resources can resolve relative paths");
    }
}
