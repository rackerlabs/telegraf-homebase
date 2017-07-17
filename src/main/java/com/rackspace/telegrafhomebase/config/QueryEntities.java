package com.rackspace.telegrafhomebase.config;

import lombok.Data;
import org.apache.ignite.cache.QueryEntity;

import java.util.Collection;

/**
 * Just a holder of {@link QueryEntity} collections for ease of spring bean creation/autowiring
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Data
public class QueryEntities {
    final Collection<QueryEntity> queryEntities;
}
