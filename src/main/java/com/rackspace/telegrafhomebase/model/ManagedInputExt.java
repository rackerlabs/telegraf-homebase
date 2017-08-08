package com.rackspace.telegrafhomebase.model;

import lombok.Data;
import lombok.experimental.Delegate;

import java.util.Collection;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class ManagedInputExt {

    @Delegate
    final ManagedInput base;

    /**
     * The telegraf(s) that are running this managed input.
     * Regional inputs only run on one, but tag-assigned may have been assigned out to multiple, one, or no telegrafs.
     */
    Collection<String> runningOn;

}
