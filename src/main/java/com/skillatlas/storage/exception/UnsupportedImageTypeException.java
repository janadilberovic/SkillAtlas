package com.skillatlas.storage.exception;

// The uploaded bytes are not a PNG, JPEG or WEBP. Decided by magic bytes, not by the
// Content-Type the client sent — see ImageType.
public class UnsupportedImageTypeException extends RuntimeException {

    public UnsupportedImageTypeException() {
        super("Only PNG, JPEG or WEBP images are accepted");
    }
}
