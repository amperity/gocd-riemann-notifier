package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class GoApiAccessor {
    private static Logger LOGGER = Logger.getLoggerFor(GoApiAccessor.class);
    private PluginConfig pluginConfig = null;

    public GoApiAccessor(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public GoApiAccessor() {
        this(new PluginConfig());
    }

    public JsonElement get(String path) throws IOException {
        HttpURLConnection con = this.getConnection(path);
        con.setRequestProperty("Accept", "application/vnd.go.cd+json");
        con.connect();
        if (200 != con.getResponseCode()) {
            LOGGER.error("Error connecting to GoCD API: " + con.getResponseCode() + " " + con.getResponseMessage());
        }
        return JsonParser.parseReader(new InputStreamReader(con.getInputStream()));
    }

    public HttpURLConnection getConnection(String path) throws IOException {
        URL url = new URL("http", "localhost", 8153, path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        if (this.authRequired()) {
            con.setRequestProperty("Authorization", this.getAuthHeader());
        }
        return con;
    }

    private boolean authRequired() {
        return this.pluginConfig.getUsername() != null && this.pluginConfig.getPassword() != null;
    }

    private String getAuthHeader() {
        String authString = this.pluginConfig.getUsername() + ":" + this.pluginConfig.getPassword();
        return Base64.getEncoder().encodeToString(authString.getBytes());
    }

}
