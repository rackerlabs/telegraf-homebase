package com.rackspace.telegrafhomebase.shared;

import com.rackspace.telegrafhomebase.QueueNames;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public class DistributedQueueUtils {

    public static String derivePendingConfigQueueName(String region) {
        return QueueNames.PREFIX_PENDING_CONFIG + region;
    }

    public static String deriveAssignedQueueName(String tid) {
        return QueueNames.PREFIX_ASSIGNMENTS + tid;
    }
}
