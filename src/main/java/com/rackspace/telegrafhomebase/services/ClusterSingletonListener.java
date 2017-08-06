package com.rackspace.telegrafhomebase.services;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
public interface ClusterSingletonListener {
    void handleGainedLeadership() throws Exception;

    void handleLostLeadership() throws Exception;
}
