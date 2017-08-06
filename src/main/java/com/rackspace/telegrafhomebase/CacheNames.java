package com.rackspace.telegrafhomebase;

import com.rackspace.telegrafhomebase.model.ManagedInput;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public class CacheNames {
    public static final String RUNNING_REMOTE_INPUTS = "running-remote-inputs";
    /**
     * Used for consitency checking tracks (non-persisted) if a given check has already been queued up for
     * config claiming.
     */
    public static final String QUEUED = "queued";
    public static final String MANAGED_INPUTS = "managed-inputs";
    public static final String TAGGED_NODES = "tagged-nodes";
    public static final String MANAGED_INPUT_QUERY = ManagedInput.class.getSimpleName();
}
