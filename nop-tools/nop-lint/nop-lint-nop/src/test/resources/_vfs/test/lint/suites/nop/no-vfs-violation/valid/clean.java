package demo;

import io.nop.core.resource.IResource;

class Clean {

    String read(IResource file) {
        return file.readText();
    }

    java.util.List<IResource> list(IResource dir) {
        return dir.depthIterator();
    }

}
