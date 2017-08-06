package com.rackspace.telegrafhomebase.shared;

import com.rackspace.telegrafhomebase.model.StructuredInputConfig;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
public class StructuredInputFormatterTest {
    @Test
    public void testTypical() throws Exception {
        final StructuredInputConfig config = new StructuredInputConfig();
        config.setPlugin("http_response");

        final Map<String, Object> fields = new HashMap<>();
        config.setFields(fields);
        fields.put("address", "https://www.github.com");
        fields.put("response_timeout", "5s");
        fields.put("method", "GET");
        fields.put("follow_redirects", true);

        final StructuredInputFormatter formatter = new StructuredInputFormatter();
        final String result = formatter.format(config);

        assertEquals("[[inputs.http_response]]\n" +
                             "  response_timeout = \"5s\"\n" +
                             "  address = \"https://www.github.com\"\n" +
                             "  method = \"GET\"\n" +
                             "  follow_redirects = true\n", result);
    }

}