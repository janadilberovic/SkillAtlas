package com.skillatlas.storage.exception;

// Second line of defence. spring.servlet.multipart.max-file-size already stops oversized bodies at
// Tomcat; this one keeps the rule visible in the domain and survives a change of transport.
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException(long maxBytes) {
        super("Image exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit");
    }
}
