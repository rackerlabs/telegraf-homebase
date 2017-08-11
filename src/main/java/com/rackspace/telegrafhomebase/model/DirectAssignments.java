package com.rackspace.telegrafhomebase.model;

import lombok.ToString;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@ToString
public class DirectAssignments {

    final Set<String/*mid*/> managedInputIds;

    public DirectAssignments() {
        managedInputIds = null;
    }

    public DirectAssignments(String managedInputId) {
        this.managedInputIds = Collections.singleton(managedInputId);
    }

    private DirectAssignments(Set<String> managedInputIds) {
        this.managedInputIds = managedInputIds;
    }

    /**
     * Adds the given managed input ID by making a deep clone of itself.
     * @param mid the managed input ID
     * @return a new instance if added or itself if already present
     */
    public DirectAssignments add(String mid) {
        if (managedInputIds == null) {
            return new DirectAssignments(Collections.singleton(mid));
        } else if (!managedInputIds.contains(mid)) {
            final HashSet<String> content = new HashSet<>(managedInputIds);
            content.add(mid);
            return new DirectAssignments(content);
        }
        else {
            return this;
        }
    }

    public DirectAssignments remove(String mid) {
        if (managedInputIds == null) {
            return this;
        } else if (managedInputIds.contains(mid)) {
            final HashSet<String> content = new HashSet<>(managedInputIds);
            content.remove(mid);
            return new DirectAssignments(content);
        } else {
            return this;
        }
    }

    public Set<String> get() {
        return managedInputIds;
    }

    public Set<String> additionsIn(DirectAssignments updatedAssignments) {
        if (this.managedInputIds == null || updatedAssignments == null || updatedAssignments.managedInputIds == null) {
            return Collections.emptySet();
        }
        final HashSet<String> content = new HashSet<>(updatedAssignments.managedInputIds);
        content.removeAll(this.managedInputIds);
        return content.isEmpty() ? null : content;
    }

    public Set<String> removalsIn(DirectAssignments updatedAssignments) {
        if (this.managedInputIds == null || updatedAssignments.managedInputIds == null) {
            return null;
        }
        final HashSet<String> content = new HashSet<>(this.managedInputIds);
        content.removeAll(updatedAssignments.managedInputIds);
        return content.isEmpty() ? null : content;
    }
}
