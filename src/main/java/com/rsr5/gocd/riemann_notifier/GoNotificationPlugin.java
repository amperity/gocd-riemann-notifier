package com.rsr5.gocd.riemann_notifier;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import io.riemann.riemann.client.IPromise;
import io.riemann.riemann.client.RiemannClient;
import io.riemann.riemann.Proto.Event;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


@Extension
public class GoNotificationPlugin implements GoPlugin {

    private static Logger LOGGER = Logger.getLoggerFor(GoNotificationPlugin
            .class);

    public static final String EXTENSION_TYPE = "notification";
    private static final List<String> goSupportedVersions = Collections
            .singletonList("1.0");
    public static final String REQUEST_NOTIFICATIONS_INTERESTED_IN =
            "notifications-interested-in";
    public static final String REQUEST_STAGE_STATUS = "stage-status";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    protected RiemannClient riemann = null;

    protected PipelineDetailsPopulator populator = null;
    protected PipelineStatePopulator statePopulator = null;


    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor
                                                        goApplicationAccessor) {
        LOGGER.info("Initializing plugin.");
        if (riemann == null) {

            PluginConfig pluginConfig = new PluginConfig();
            int riemannPort = pluginConfig.getRiemannPort();
            String riemannHost = pluginConfig.getRiemannHost();

            this.populator = new PipelineDetailsPopulator();
            this.statePopulator = new PipelineStatePopulator();
            try {
                riemann = RiemannClient.tcp(riemannHost, riemannPort);
                riemann.connect();
            } catch (IOException e) {
                LOGGER.warn("Unable to connect to Riemann at " + riemannHost);
            }

            /* Setup the pipeline state time */
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    fetchCurrentState();
                }
            }, 5000, pluginConfig.getFetchInterval());
        }
    }

    protected void fetchCurrentState() {
        LOGGER.info("Fetching current state.");
        HashMap<String, String> states = null;
        try {
            states = statePopulator.getStageStates();
        } catch (IOException e) {
            LOGGER.warn("Couldn't fetch RSS! " + e.getMessage());
            return;
        }

        Iterator<Map.Entry<String, String>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();

            try{

                if (!riemann.isConnected()) {
                    riemann.connect();
                }

                riemann.event().
                        service("gocd:" + pair.getKey().toString()).
                        state(pair.getValue().toString()).
                        description("Pipeline state update.").
                        send().
                        deref(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (IOException e) {
                LOGGER.error("Failed to send update to Riemann", e);
            }

            it.remove(); // avoids a ConcurrentModificationException
        }
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        LOGGER.debug("received go plugin api request " + goPluginApiRequest
                .requestName());

        if (goPluginApiRequest.requestName().equals
                (REQUEST_NOTIFICATIONS_INTERESTED_IN))
            return handleNotificationsInterestedIn();
        if (goPluginApiRequest.requestName().equals(REQUEST_STAGE_STATUS)) {
            return handleStageNotification(goPluginApiRequest);
        }
        return null;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        LOGGER.debug("received pluginIdentifier request");

        return new GoPluginIdentifier(EXTENSION_TYPE, goSupportedVersions);
    }

    private GoPluginApiResponse handleNotificationsInterestedIn() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("notifications", Collections.singletonList
                (REQUEST_STAGE_STATUS));
        LOGGER.debug("requesting details of stage-status notifications");
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private static String safeGetAsString(JsonObject json, String property) {
        JsonElement element = json.get(property);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    private static Instant safeInstantParse(String s) {
        if (s != null && !s.equals("")) {
            try {
                return Instant.parse(s);
            } catch(Exception e) {
                LOGGER.debug("Encountered unparseable instant value", e);
            }
        }
        return null;
    }

    static final class StageData {
        public final String pipeline;
        public final String stage;
        public final String state;
        public final String counter;
        public final Instant start;
        public final Instant end;
        public final List<JobData> jobs;

        final class JobData {
            public final String name;
            public final String result;
            public final Instant start;
            public final Instant end;

            public JobData(JsonObject json) {
                String start = safeGetAsString(json, "schedule-time");
                String end = safeGetAsString(json, "complete-time");

                this.name = safeGetAsString(json, "name");
                this.result = safeGetAsString(json, "result");
                this.start = safeInstantParse(start);
                this.end = safeInstantParse(end);
            }

            public Long getDurationInSeconds() {
                if (this.start != null && this.end != null) {
                    return ChronoUnit.SECONDS.between(this.start, this.end);
                }
                return null;
            }
        }

        public StageData(JsonObject json) {
            JsonObject pipelineObject = (JsonObject) json.get("pipeline");
            JsonObject stageObject = (JsonObject) pipelineObject.get("stage");
            String start = safeGetAsString(stageObject, "create-time");
            String end = safeGetAsString(stageObject, "last-transition-time");
            List<JobData> jobs = new ArrayList<JobData>();

            this.pipeline = safeGetAsString(pipelineObject, "name");
            this.counter = safeGetAsString(pipelineObject, "counter");
            this.stage = safeGetAsString(stageObject, "name");
            this.state = safeGetAsString(stageObject, "state");
            this.start = safeInstantParse(start);
            this.end = safeInstantParse(end);

            JsonArray jobsArray = stageObject.getAsJsonArray("jobs");
            if (jobsArray != null) {
                for (JsonElement job : jobsArray) {
                    jobs.add(new JobData((JsonObject) job));
                }
            }
            this.jobs = Collections.unmodifiableList(jobs);
        }

        public String getPipelineServiceName() {
            return "gocd:" + this.pipeline + ":" + this.stage;
        }

        public Long getDurationInSeconds() {
            if (this.start != null && this.end != null) {
                return ChronoUnit.SECONDS.between(this.start, this.end);
            }
            return null;
        }
    }

    protected GoPluginApiResponse handleStageNotification(
            GoPluginApiRequest goPluginApiRequest) {

        int responseCode = SUCCESS_RESPONSE_CODE;

        Map<String, Object> response = new HashMap<>();
        List<String> messages = new ArrayList<>();

        response.put("status", "success");

        JsonObject json = populator.extendMessage(goPluginApiRequest
                .requestBody());

        try {
            List<Event> events = new ArrayList<>();
            StageData notification = new StageData(json);

            events.add(riemann.event().
                       service(notification.getPipelineServiceName()).
                       state(notification.state).
                       description(json.toString()).
                       build());

            if (notification.state.equals("Failed") ||
                notification.state.equals("Passed")) {

                // output stage timing - add all the stage timings together
                // for a given (pipeline, run-id) pair to get total pipeline
                // execution time
                events.add(riemann.event().
                           service("gocd:stage-duration").
                           time(notification.start.getEpochSecond()).
                           state(notification.state).
                           metric(notification.getDurationInSeconds()).
                           attribute("pipeline", notification.pipeline).
                           attribute("stage", notification.stage).
                           attribute("run-id", notification.counter).
                           build());

                // jobs in a stage can run in parallel so this metric isn't
                // necessarily useful for summation, but enables analysis of
                // what's making a stage take a long time (and what isn't),
                // for example.
                for (StageData.JobData job : notification.jobs) {
                    events.add(riemann.event().
                               service("gocd:job-duration").
                               time(job.start.getEpochSecond()).
                               state(job.result).
                               metric(job.getDurationInSeconds()).
                               attribute("pipeline", notification.pipeline).
                               attribute("stage", notification.stage).
                               attribute("job", job.name).
                               attribute("run-id", notification.counter).
                               build());
                }
            }

            riemann.sendEvents(events).
                    deref(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            LOGGER.error("failed to notify pipeline listener", e);
            responseCode = INTERNAL_ERROR_RESPONSE_CODE;
            response.put("status", "failure");
            messages.add(e.getMessage());
        }

        response.put("messages", messages);
        return renderJSON(responseCode, response);
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object
            response) {
        final String json = response == null ? null : new GsonBuilder()
                .create().toJson(response);
        return new GoPluginApiResponse() {
            public int responseCode() {
                return responseCode;
            }

            public Map<String, String> responseHeaders() {
                return null;
            }

            public String responseBody() {
                return json;
            }
        };
    }
}
