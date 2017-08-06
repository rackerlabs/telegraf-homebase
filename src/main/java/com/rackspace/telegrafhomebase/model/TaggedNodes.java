package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class TaggedNodes {
    Set<String> tids = new HashSet<>();
}
