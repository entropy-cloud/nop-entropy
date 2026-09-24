package demo;

class Clean {
    ProcessBuilder build(String... command) {
        return new ProcessBuilder(command);
    }
}
