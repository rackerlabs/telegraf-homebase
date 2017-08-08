package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class RunningAssignedInputKey implements Serializable {
    static final long serialVersionUID = 0L;

    final String managedInputId;
    final String telegrafId;
}
