package com.rackspace.telegrafhomebase;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public class CacheNames {
    public static final String RUNNING = "running";
    /**
     * Used for consitency checking tracks (non-persisted) if a given check has already been queued up for
     * config claiming.
     */
    public static final String QUEUED = "queued";
    public static final String REGIONAL_CONFIG = "regional-config";
}
