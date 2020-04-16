package com.rsr5.gocd.riemann_notifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;

public class JUnitResultsParser {
    private ObjectMapper objectMapper;
    private XmlMapper xmlMapper;

    public JUnitResultsParser() {
        this.objectMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.registerModule(new SimpleModule().addDeserializer(
            JsonNode.class,
            new DuplicateToArrayJsonNodeDeserializer()
        ));
    }

    public String fromXml(String xmlStr) throws IOException {
        JsonNode node = xmlMapper.readTree(xmlStr);
        return objectMapper.writeValueAsString(node);
    }
}
