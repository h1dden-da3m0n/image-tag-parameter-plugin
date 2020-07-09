package io.jenkins.plugins.luxair.model;

import io.jenkins.plugins.luxair.logic.ImageTagService;
import kong.unirest.*;

import java.util.logging.Logger;


public class ErrorInterceptor implements Interceptor {
    private static final Logger logger = Logger.getLogger(ImageTagService.class.getName());

    @Override
    public HttpResponse onFail(Exception e, HttpRequestSummary request, Config config) {
        logger.severe(e.getMessage());
        return new FailedResponse(e);
    }
}