package io.github.nicolassanchez1.technicaltestdavivienda.documents.application;

/**
 * One file as the request presented it: the name the client sent, the size it declared and a way
 * to read the bytes. The use case takes this instead of a servlet type so it stays testable
 * without a web layer.
 */
public record UploadedFile(String filename, long sizeBytes, FileContent content) {}
