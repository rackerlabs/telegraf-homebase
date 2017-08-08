package com.rackspace.telegrafhomebase.model;

import lombok.Data;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

import java.io.Serializable;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Data
public class RunningRegionalInputKey implements Serializable {
    static final long serialVersionUID = 0L;

    /**
     * The managed input's ID
     */
    @QuerySqlField(index = true)
    final String mid;
    @QuerySqlField(index = true)
    final String region;
}
