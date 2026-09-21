package demo;

class Clean {

    int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new NopAiException("Failed to parse number", e);
        }
    }

    void tolerated() {
        try {
            probe();
        } catch (IllegalStateException e) {
            // intentionally ignored: probe failure is non-fatal
        }
    }

}
