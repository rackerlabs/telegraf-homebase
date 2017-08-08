package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class ConnectedNode implements Serializable {
    static final long serialVersionUID = 0L;

    String tenantId;
    String region;
    Map<String,String> tags;
    String clusterNodeId;
}
