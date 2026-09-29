package io.github.nicolassanchez1.technicaltestdavivienda.shared.web;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "urn:problem-type:resource-not-found", message);
    }
}
