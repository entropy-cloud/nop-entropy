package demo;

import java.io.File;

class Boundary {

    void touch(java.util.List<String> paths) throws java.io.IOException {
        File f = new File("x.txt");
        new FileInputStream(f);
        java.nio.file.Path p = java.nio.file.Paths.get("y");
        Files.exists(p);
        String rel = p.relativize(p).toString();
        for (String path : paths) {
            new File(path).toPath();
        }
    }

}
