package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

public class PipelineDetailsPopulator {
    private GoApiAccessor accessor = null;

    public PipelineDetailsPopulator(GoApiAccessor accessor) {
        this.accessor = accessor;
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

            String pipeline = pipelineObject.get("name").getAsString();
            String pipelineCounter = pipelineObject.get("counter")
                    .getAsString();
            String stage = stageObject.get("name").getAsString();
            String stageCounter = stageObject.get("counter").getAsString();

            json.add("x-pipeline-artifacts", new JsonObject());
            JsonArray jobArtifacts = new JsonArray();

            JsonArray jobs = stageObject.get("jobs").getAsJsonArray();
            for(final JsonElement job : jobs) {

                JsonObject extraDetails = downloadPipelineArtifacts(pipeline, stage,
                        pipelineCounter, stageCounter, job.getAsJsonObject().get("name").getAsString());
                jobArtifacts.add(extraDetails);
            }

            json = mergeInPipelineInstanceDetails("x-pipeline-artifacts",
                    json, jobArtifacts);
        } catch (IOException e) {
            json.addProperty("x-pipeline-error", "Error connecting to GoCD " +
                    "API.");
        }

        return json;
    }
}
