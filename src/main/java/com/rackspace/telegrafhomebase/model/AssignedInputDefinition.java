package com.rackspace.telegrafhomebase.model;

import lombok.Data;

import javax.validation.constraints.Size;
import java.util.Map;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Data
public class AssignedInputDefinition {
    /**
     * The actual telegraf input definition which MUST be an [[inputs.*]] block.
     */
    String text;

    StructuredInputConfig structured;

    String title;

    @Size(min = 1)
    Map<String,String> assignmentTags;
}
