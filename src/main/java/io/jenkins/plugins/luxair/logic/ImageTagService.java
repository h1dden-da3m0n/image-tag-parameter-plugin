package io.jenkins.plugins.luxair.logic;

import hudson.util.VersionNumber;
import io.jenkins.plugins.luxair.model.*;
import kong.unirest.*;
import kong.unirest.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ImageTagService {

    private static final Logger logger = Logger.getLogger(ImageTagService.class.getName());
    private static final Interceptor errorInterceptor = new ErrorInterceptor();

    private ImageTagService() {
        throw new IllegalStateException("Utility class");
    }

    public static ErrorContainer<List<ImageTag>> getTags(String image, String registry, String filter,
                                                         String user, String password, Ordering ordering) {
        ErrorContainer<List<ImageTag>> container = new ErrorContainer<>(Collections.emptyList());

        ErrorContainer<AuthService> authService = getAuthService(registry);
        Optional<String> authServiceError = authService.getErrorMsg();
        if (authServiceError.isPresent()) {
            container.setErrorMsg(authServiceError.get());
            return container;
        }

        ErrorContainer<String> token = getAuthToken(authService.getValue(), image, user, password);
        Optional<String> tokenError = token.getErrorMsg();
        if (tokenError.isPresent()) {
            container.setErrorMsg(tokenError.get());
            return container;
        }

        ErrorContainer<List<VersionNumber>> tags = getImageTagsFromRegistry(image, registry, authService.getValue().getAuthType(), token.getValue());
        Optional<String> tagsError = tags.getErrorMsg();
        if (tagsError.isPresent()) {
            container.setErrorMsg(tagsError.get());
            return container;
        }

        ErrorContainer<List<ImageTag>> filterTags = filterTags(image, tags.getValue(), filter, ordering);
        filterTags.getErrorMsg().ifPresent(container::setErrorMsg);
        container.setValue(filterTags.getValue());
        return container;
    }

    private static ErrorContainer<List<ImageTag>> filterTags(String image, List<VersionNumber> tags,
                                                             String filter, Ordering ordering) {
        ErrorContainer<List<ImageTag>> container = new ErrorContainer<>(Collections.emptyList());
        logger.fine("Ordering Tags according to: " + ordering);

        if (ordering == Ordering.NATURAL || ordering == Ordering.REV_NATURAL) {
            container.setValue(tags.stream()
                .map(VersionNumber::toString)
                .filter(tag -> tag.matches(filter))
                .sorted(ordering == Ordering.NATURAL ? Collections.reverseOrder() : String::compareTo)
                .map(tag -> new ImageTag(image, tag))
                .collect(Collectors.toList()));
        } else {
            try {
                container.setValue(tags.stream()
                    .filter(tag -> tag.toString().matches(filter))
                    .sorted(ordering == Ordering.ASC_VERSION ? VersionNumber::compareTo : VersionNumber.DESCENDING)
                    .map(tag -> new ImageTag(image, tag.toString()))
                    .collect(Collectors.toList()));
            } catch (Exception ignore) {
                logger.warning("Unable to cast ImageTags to versions! Versioned Ordering is not supported for this images tags.");
                container.setErrorMsg("Unable to cast ImageTags to versions! Versioned Ordering is not supported for this images tags.");
            }
        }

        return container;
    }

    private static ErrorContainer<AuthService> getAuthService(String registry) {
        ErrorContainer<AuthService> container = new ErrorContainer<>(new AuthService(AuthType.UNKNOWN));
        String url = registry + "/v2/";
        String type = "";

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        String headerValue = Unirest.get(url).asEmpty()
            .getHeaders().getFirst("Www-Authenticate");
        Unirest.shutDown();

        String typePattern = "^(\\S+)";
        Matcher typeMatcher = Pattern.compile(typePattern).matcher(headerValue);
        if (typeMatcher.find()) {
            type = typeMatcher.group(1);
        }

        if (type.equals(AuthType.BASIC.value)) {
            container.getValue().setAuthType(AuthType.BASIC);
            logger.fine("AuthService: type=Basic");
        } else if (type.equals(AuthType.BEARER.value)) {
            String pattern = "Bearer realm=\"(\\S+)\",service=\"(\\S+)\"";
            Matcher m = Pattern.compile(pattern).matcher(headerValue);
            if (m.find()) {
                container.getValue().setAuthType(AuthType.BEARER);
                container.getValue().setRealm(m.group(1));
                container.getValue().setService(m.group(2));
                logger.fine("AuthService: type=Bearer, realm=" + m.group(1) + ", service=" + m.group(2));
            } else {
                logger.warning("No AuthService available from " + url);
            }
        } else {
            container.setErrorMsg("Unknown authorization type! Received type: " + type);
            logger.warning("Unknown authorization type! Received type: " + type);
        }

        return container;
    }

    private static ErrorContainer<String> getAuthToken(AuthService authService, String image,
                                                       String user, String password) {
        ErrorContainer<String> container = new ErrorContainer<>("");

        switch (authService.getAuthType()) {
            case BASIC:
                container.setValue(Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
                break;
            case BEARER:
                ErrorContainer<String> bearer = getBearerAuthToken(authService, image, user, password);
                bearer.getErrorMsg().ifPresent(container::setErrorMsg);
                container.setValue(bearer.getValue());
                break;
            default:
                container.setErrorMsg("AuthServiceType is unknown. Unable to fetch AuthToken.");
                logger.warning("AuthServiceType is unknown. Unable to fetch AuthToken.");
        }

        return container;
    }

    private static ErrorContainer<String> getBearerAuthToken(AuthService authService, String image,
                                                             String user, String password) {
        ErrorContainer<String> container = new ErrorContainer<>("");

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        GetRequest request = Unirest.get(authService.getRealm());
        if (!user.isEmpty() && !password.isEmpty()) {
            logger.fine("Using Basic authentication to fetch AuthToken");
            request = request.basicAuth(user, password);
        }
        HttpResponse<JsonNode> response = request
            .queryString("service", authService.getService())
            .queryString("scope", "repository:" + image + ":pull")
            .asJson();
        if (response.isSuccess()) {
            ErrorContainer<String> token = findTokenInResponse(response, "token", "access_token");
            token.getErrorMsg().ifPresent(container::setErrorMsg);
            container.setValue(token.getValue());
        } else {
            container.setErrorMsg("Request failed! Token was not received");
            logger.warning("Request failed! Token was not received");
        }
        Unirest.shutDown();

        return container;
    }

    private static ErrorContainer<String> findTokenInResponse(HttpResponse<JsonNode> response, String... searchKey) {
        ErrorContainer<String> container = new ErrorContainer<>("");
        JSONObject jsonObj = response.getBody().getObject();

        for (String key : searchKey) {
            if (jsonObj.has(key)) {
                logger.fine("Token received");
                container.setValue(jsonObj.getString(key));
                return container;
            }
        }

        container.setErrorMsg("Unable to find token in response! Token was not received");
        logger.warning("Unable to find token in response! Token was not received");
        return container;
    }

    private static ErrorContainer<List<VersionNumber>> getImageTagsFromRegistry(String image, String registry,
                                                                                AuthType authType, String token) {
        ErrorContainer<List<VersionNumber>> container = new ErrorContainer<>(new ArrayList<>());
        String url = registry + "/v2/{image}/tags/list";

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        HttpResponse<JsonNode> response = Unirest.get(url)
            .header("Authorization", authType + " " + token)
            .routeParam("image", image)
            .asJson();
        if (response.isSuccess()) {
            logger.fine("HTTP status: " + response.getStatusText());
            response.getBody().getObject()
                .getJSONArray("tags")
                .forEach(item -> container.getValue().add(new VersionNumber(item.toString())));
        } else {
            container.setErrorMsg("Image tags request responded with HTTP status: " + response.getStatusText());
            logger.warning("Image tags request responded with HTTP status: " + response.getStatusText());
        }

        Unirest.shutDown();
        return container;
    }
}