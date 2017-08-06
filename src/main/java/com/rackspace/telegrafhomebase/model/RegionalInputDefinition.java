package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class RegionalInputDefinition {
    /**
     * The actual telegraf input definition which MUST be an [[inputs.*]] block.
     */
    String text;

    StructuredInputConfig structured;

    String title;

    @Size(min = 1)
    List<String> regions;
}
