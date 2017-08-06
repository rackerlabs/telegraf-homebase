package com.rackspace.telegrafhomebase.shared;

import com.rackspace.telegrafhomebase.model.StructuredInputConfig;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
public class StructuredInputFormatter {

    public String format(StructuredInputConfig inputConfig) {
        final StringBuilder sb = new StringBuilder();
        sb.append("[[inputs.")
                .append(inputConfig.getPlugin())
                .append("]]\n");
        inputConfig.getFields().forEach((k,v)->{
            sb.append("  ")
                    .append(k)
                    .append(" = ");
            if (v instanceof String) {
                sb.append("\"");
                sb.append(v);
                sb.append("\"");
            } else if ((v instanceof Number) || (v instanceof Boolean)) {
                sb.append(v.toString());
            }
            sb.append("\n");
        });

        return sb.toString();
    }
}
