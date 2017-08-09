package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class TaggedNodes implements Serializable {
    static final long serialVersionUID = 0L;

    Set<String> tids = new HashSet<>();
}
