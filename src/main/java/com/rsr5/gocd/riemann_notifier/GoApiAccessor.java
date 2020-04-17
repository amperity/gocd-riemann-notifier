package com.rsr5.gocd.riemann_notifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GoApiAccessor {
    public JsonElement get(String path) throws IOException {
        URL url = new URL("http", "localhost", 8153, path);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Accept", "application/vnd.go.cd+json");
        con.connect();
        return JsonParser.parseReader(new InputStreamReader(con.getInputStream()));
    }
}
