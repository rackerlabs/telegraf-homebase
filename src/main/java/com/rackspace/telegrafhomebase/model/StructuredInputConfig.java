package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class StructuredInputConfig implements Serializable {
    static final long serialVersionUID = 0L;

    String plugin;

    Map<String, Object> fields;
}
