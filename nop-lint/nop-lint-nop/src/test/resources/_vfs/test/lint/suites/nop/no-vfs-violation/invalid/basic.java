package demo;

import java.nio.file.Files;

class Basic {

    String read(java.nio.file.Path p) {
        return Files.readString(p);
    }

}
