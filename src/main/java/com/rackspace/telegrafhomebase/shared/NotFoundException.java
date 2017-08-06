package com.rackspace.telegrafhomebase.shared;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
@Getter
public class NotFoundException extends Exception {
    private final String id;

    public NotFoundException(String message, String id) {
        super(message);
        this.id = id;
    }
}
