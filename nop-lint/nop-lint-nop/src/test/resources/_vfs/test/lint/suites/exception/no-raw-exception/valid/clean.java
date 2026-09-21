package demo;

class Clean {

    int parse(String raw) {
        if (raw == null) {
            throw new NopException(MyErrors.ERR_INVALID_ARG);
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new NopAiException("Failed to parse number", e);
        }
    }

}
