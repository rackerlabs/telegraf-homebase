package com.rackspace.telegrafhomebase.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
public class DirectAssignments {

    private final Set<String/*mid*/> managedInputIds;

    public DirectAssignments() {
        managedInputIds = null;
    }

    public DirectAssignments(String mid) {
        managedInputIds = Collections.singleton(mid);
    }

    private DirectAssignments(Set<String/*mid*/> src) {
        managedInputIds = src;
    }

    public DirectAssignments add(String mid) {
        Set<String/*mid*/> content = new HashSet<>(managedInputIds);
        content.add(mid);
        return new DirectAssignments(content);
    }

    public DirectAssignments remove(String mid) {
        Set<String/*mid*/> content = new HashSet<>(managedInputIds);
        content.remove(mid);
        return new DirectAssignments(content);
    }
}
