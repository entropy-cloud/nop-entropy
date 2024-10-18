package io.nop.http.client.jdk;

import io.nop.commons.util.StringHelper;

import java.io.IOException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;

public class MultipartBodyPublisher implements BodyPublisher {
    private static final String BOUNDARY = UUID.randomUUID().toString();
    private final List<Part> parts = new ArrayList<>();

    static class Part {
        final String headers;
        final BodyPublisher bodyPublisher;

        Part(String headers, BodyPublisher bodyPublisher) {
            this.headers = headers;
            this.bodyPublisher = bodyPublisher;
        }
    }

    public void addPart(String name, String value) {
        name = StringHelper.encodeURL(name);
        String headers = "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
        parts.add(new Part(headers, BodyPublishers.ofString(value)));
    }

    public void addPart(String name, String mimeType, Path file) throws IOException {
        name = StringHelper.encodeURL(name);
        //String mimeType = Files.probeContentType(file);
        String encodedFileName = StringHelper.encodeURL(file.getFileName().toString());
        String headers = "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + encodedFileName + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n";
        parts.add(new Part(headers, BodyPublishers.ofFile(file)));
    }

    @Override
    public long contentLength() {
        return -1; // unknown length
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        List<BodyPublisher> bodyPublishers = new ArrayList<>();
        for (Part part : parts) {
            bodyPublishers.add(BodyPublishers.ofString("--" + BOUNDARY + "\r\n" + part.headers));
            bodyPublishers.add(part.bodyPublisher);
            bodyPublishers.add(BodyPublishers.ofString("\r\n"));
        }
        bodyPublishers.add(BodyPublishers.ofString("--" + BOUNDARY + "--\r\n"));
        BodyPublisher publisher = new ConcatenatedBodyPublisher(bodyPublishers);
        publisher.subscribe(subscriber);
    }

    public static String getBoundary() {
        return BOUNDARY;
    }
}