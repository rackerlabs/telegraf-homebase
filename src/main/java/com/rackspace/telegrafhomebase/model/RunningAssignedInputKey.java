package com.rackspace.telegrafhomebase.model;

import lombok.Data;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

import java.io.Serializable;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class RunningAssignedInputKey implements Serializable {
    static final long serialVersionUID = 0L;

    @QuerySqlField(index = true)
    final String managedInputId;

    @QuerySqlField
    final String telegrafId;
}
