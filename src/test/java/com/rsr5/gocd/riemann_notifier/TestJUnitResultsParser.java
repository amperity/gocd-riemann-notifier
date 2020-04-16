package com.rsr5.gocd.riemann_notifier;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJUnitResultsParser {
    private String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded);
    }

    @Test
    public void testFromXml() throws IOException {
        JUnitResultsParser parser = new JUnitResultsParser();

        String input = this.readFile("src/test/junit_results.xml");
        String expected = this.readFile("src/test/junit_results.json");

        String actual = parser.fromXml(input);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode expectedNode = objectMapper.readTree(expected);
        JsonNode actualNode = objectMapper.readTree(actual);

        if (!expectedNode.equals(actualNode)) {
            fail("JUnit parsing failed. Expected: " + expectedNode.toString() +
                    ", got: " + actualNode.toString());
        }
    }
}
