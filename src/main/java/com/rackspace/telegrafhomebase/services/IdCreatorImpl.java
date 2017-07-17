package com.rackspace.telegrafhomebase.services;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
public class IdCreatorImpl implements IdCreator {
    @Override
    public String create() {
        return UUID.randomUUID().toString();
    }
}
