package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class TestPipelineDetailsPopulator {

    private String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded);
    }

    @Test
    public void test_pipeline_details_populator() throws IOException {
        GoApiAccessor accessor = mock(GoApiAccessor.class);

        PipelineDetailsPopulator pipelineDetailsPopulator = new
                PipelineDetailsPopulator(accessor);

        String contentArtifacts = "{}";
        String requestBody = "{}";
        try {
            requestBody = this.readFile("src/test/example_notification.json");
        } catch (IOException e) {
            System.out.println("can't load file example_notification.json");
        }

        try {
            contentArtifacts = this.readFile("src/test/test_contents_artifacts.json");
        } catch (IOException e) {
            System.out.println("can't load file test_contents_artifacts.json");
        }

        try {
            when(accessor.get("/go/files/pipeline1/1/stage1/1/job1.json"))
                    .thenReturn(JsonParser.parseString(contentArtifacts));
        } catch (IOException e) {
            // This will not happen, because Mockito.
        }

        JsonObject json = pipelineDetailsPopulator.extendMessage(requestBody);

        verify(accessor).get("/go/files/pipeline1/1/stage1/1/job1.json");

        assert (json.has("x-pipeline-artifacts"));
    }

    @Test
    public void test_pipeline_details_error() throws IOException {
        GoApiAccessor accessor = mock(GoApiAccessor.class);

        PipelineDetailsPopulator pipelineDetailsPopulator = new PipelineDetailsPopulator(accessor);

        String requestBody = "{}";
        try {
            requestBody = this.readFile("src/test/example_notification.json");
        } catch (IOException e) {
            System.out.println("can't load file example_notification.json");
        }

        try {
            when(accessor.get("/go/files/pipeline1/1/stage1/1/job1.json"))
                .thenThrow(IOException.class);
        } catch (IOException e) {
            // This will not happen, because Mockito.
        }

        JsonObject json = pipelineDetailsPopulator.extendMessage(requestBody);

        verify(accessor).get("/go/files/pipeline1/1/stage1/1/job1.json");

        assert (json.has("x-pipeline-error"));
    }
}
