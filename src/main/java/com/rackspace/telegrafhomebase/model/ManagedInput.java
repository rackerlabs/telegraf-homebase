package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Data
public class ManagedInput implements Serializable {
    static final long serialVersionUID = 0L;

    String id;
    String title;

    /**
     * An optional field that provides linkage to the group that contains this config
     */
    String groupId;

    String tenantId;

    String text;

    StructuredInputConfig structured;

    String region;

    Map<String,String> assignmentTags;
}
