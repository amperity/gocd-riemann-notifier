package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;
import java.net.URL;

public class PipelineDetailsPopulator {
    private static Logger LOGGER = Logger.getLoggerFor(PipelineDetailsPopulator.class);
    private GoApiAccessor accessor;
    private JUnitResultsParser parser;
    private PluginConfig config;

    public PipelineDetailsPopulator(GoApiAccessor accessor, JUnitResultsParser parser) {
        this.accessor = accessor;
        this.config = new PluginConfig();
        this.parser = parser;
    }

    public PipelineDetailsPopulator(GoApiAccessor accessor) {
        this(accessor, new JUnitResultsParser());
    }

    public PipelineDetailsPopulator() {
        this(new GoApiAccessor());
    }

    private JsonObject mergeInPipelineInstanceDetails(String attributeName,
                                                      JsonElement notification,
                                                      JsonElement pipelineInstance) {
        JsonObject json = notification.getAsJsonObject();
        json.add(attributeName, pipelineInstance);
        return json;
    }

    /**
     * Performs a depth-first search of the artifacts, returning a JsonObject
     * translation of the first artifact named "results.xml".
     */
    protected JsonElement findJunitResults(JsonArray artifacts) throws IOException {
        for (JsonElement element : artifacts) {
            JsonObject artifact = element.getAsJsonObject();
            if ("folder".equals(artifact.get("type").getAsString())) {
                JsonElement result = findJunitResults(artifact.get("files").getAsJsonArray());
                if (null != result) {
                    return result;
                }
            } else if ("file".equals(artifact.get("type").getAsString()) &&
                       "results.xml".equals(artifact.get("name").getAsString())) {
                URL url = new URL(artifact.get("url").getAsString());
                HttpURLConnection con = accessor.getConnection(url.getPath());
                con.connect();
                if (con.getResponseCode() != 200) {
                    LOGGER.error("Server returned error when trying to get JUnit results: " + con.getResponseMessage());
                    return null;
                }
                StringBuffer response = new StringBuffer();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }
                String junitJson = parser.fromXml(response.toString());

                try {
                    return JsonParser.parseString(junitJson);
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Failed to parse JUnit JSON results", e);
                    return null;
                }
            }
        }
        return null;
    }

    protected JsonObject downloadPipelineArtifacts(String pipelineName,
                                                   String stageName,
                                                   String pipelineCounter,
                                                   String stageCounter,
                                                   String jobName) throws IOException {
        String path = "/go/files/" + pipelineName + "/" + pipelineCounter + "/"
            + stageName + "/" + stageCounter + "/" + jobName + ".json";
        JsonElement json = accessor.get(path);

        JsonObject result = new JsonObject();
        result.add(jobName, json.getAsJsonArray());
        return result;
    }

    public JsonObject extendMessage(String requestBody) {
        JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

        try {
            JsonObject pipelineObject = json.get("pipeline").getAsJsonObject();
            JsonObject stageObject = pipelineObject.get("stage").getAsJsonObject();

            if ("Building".equals(stageObject.get("state").getAsString())) {
                return json;
            }

            String pipeline = pipelineObject.get("name").getAsString();
            String pipelineCounter = pipelineObject.get("counter")
                    .getAsString();
            String stage = stageObject.get("name").getAsString();
            String stageCounter = stageObject.get("counter").getAsString();

            json.add("x-pipeline-artifacts", new JsonObject());
            JsonArray jobArtifacts = new JsonArray();

            JsonArray jobs = stageObject.get("jobs").getAsJsonArray();
            for(final JsonElement job : jobs) {
                String jobName = job.getAsJsonObject().get("name").getAsString();

                JsonElement artifacts = downloadPipelineArtifacts(pipeline, stage, pipelineCounter, stageCounter, jobName);
                jobArtifacts.add(artifacts);

                if (null != this.config.getJUnitJobWhitelist() &&
                    Pattern.matches(this.config.getJUnitJobWhitelist(), jobName)) {
                    try {
                        JsonElement junitResults = findJunitResults(artifacts.getAsJsonObject().get(jobName).getAsJsonArray());
                        if (null != junitResults) {
                            json.add("x-junit-results", junitResults);
                        }
                    } catch (IOException e) {
                        json.addProperty("x-pipeline-error", "Error downloading pipeline test history.");
                        LOGGER.error("Error downloading pipeline test history", e);
                    }
                }
            }

            json = mergeInPipelineInstanceDetails("x-pipeline-artifacts",
                    json, jobArtifacts);
        } catch (IOException e) {
            json.addProperty("x-pipeline-error", "Error connecting to GoCD API.");
            LOGGER.error("Error connecting to GoCD API", e);
        }

        return json;
    }
}
