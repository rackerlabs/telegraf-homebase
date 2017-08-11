package com.rackspace.telegrafhomebase;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public class CacheNames {
    public static final String RUNNING_REGIONAL_INPUTS = "running-regional-inputs";
    public static final String RUNNING_ASSIGNED_INPUTS = "running-assigned-inputs";
    /**
     * Used for consitency checking tracks (non-persisted) if a given check has already been queued up for
     * config claiming.
     */
    public static final String MANAGED_INPUTS = "managed-inputs";
    public static final String TAGGED_NODES = "tagged-nodes";
    public static final String CONNECTED_NODES = "connected-nodes";
    public static final String DIRECT_ASSIGNMENTS = "direct-assignments";
}
