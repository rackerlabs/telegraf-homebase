package com.rackspace.mmi.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Data
public class StoredRegionalConfig implements Serializable {
    static final long serialVersionUID = 0L;

    String id;
    String comment;

    /**
     * An optional field that provides linkage to the group that contains this config
     */
    String groupId;

    String tenantId;

    String definition;

    String region;

    /**
     * When set, indicates that this telegraf input must be run on a specific instance, usually because
     * it's an agent-self check.
     */
    String assignedTo;

    /**
     * A field that is only set by the backend during lookups to indicate what instance is actually .
     */
    String runningOn;
}
