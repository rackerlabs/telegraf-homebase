package com.rackspace.telegrafhomebase.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NotOwnedException extends Exception {

    public NotOwnedException(String message) {
        super(message);
    }
}
