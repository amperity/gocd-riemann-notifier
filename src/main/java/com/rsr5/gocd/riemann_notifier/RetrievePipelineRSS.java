package com.rsr5.gocd.riemann_notifier;

import java.io.IOException;
import java.net.HttpURLConnection;

public class RetrievePipelineRSS {
    private GoApiAccessor accessor = null;

    public RetrievePipelineRSS(GoApiAccessor accessor) {
        this.accessor = accessor;
    }

    public RetrievePipelineRSS() {
        this(new GoApiAccessor());
    }

    public HttpURLConnection download() throws IOException {
        HttpURLConnection con = accessor.getConnection("/go/cctray.xml");
        con.connect();
        return con;
    }
}
