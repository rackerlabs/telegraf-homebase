package com.rackspace.telegrafhomebase.model;

import lombok.Data;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class TaggedNodesKey {
    /**
     * The tenant that owns this particular tag
     */
    final String tenantId;
    /**
     * name of the tag
     */
    final String name;
    /**
     * value of the tag
     */
    final String value;
}
