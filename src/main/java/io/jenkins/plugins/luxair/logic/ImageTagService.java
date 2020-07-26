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

    public static ResultContainer<List<ImageTag>> getTags(String image, String registry, String filter,
                                                          String user, String password, Ordering ordering) {
        ResultContainer<List<ImageTag>> container = new ResultContainer<>(Collections.emptyList());

        ResultContainer<AuthService> authService = getAuthService(registry);
        Optional<String> authServiceError = authService.getErrorMsg();
        if (authServiceError.isPresent()) {
            container.setErrorMsg(authServiceError.get());
            return container;
        }

        ResultContainer<String> token = getAuthToken(authService.getValue(), image, user, password);
        Optional<String> tokenError = token.getErrorMsg();
        if (tokenError.isPresent()) {
            container.setErrorMsg(tokenError.get());
            return container;
        }

        ResultContainer<List<VersionNumber>> tags = getImageTagsFromRegistry(image, registry, authService.getValue().getAuthType(), token.getValue());
        Optional<String> tagsError = tags.getErrorMsg();
        if (tagsError.isPresent()) {
            container.setErrorMsg(tagsError.get());
            return container;
        }

        ResultContainer<List<ImageTag>> filterTags = filterTags(image, tags.getValue(), filter, ordering);
        filterTags.getErrorMsg().ifPresent(container::setErrorMsg);
        container.setValue(filterTags.getValue());
        return container;
    }

    private static ResultContainer<List<ImageTag>> filterTags(String image, List<VersionNumber> tags,
                                                              String filter, Ordering ordering) {
        ResultContainer<List<ImageTag>> container = new ResultContainer<>(Collections.emptyList());
        logger.fine(Messages.ImageTagService_debug_orderingTagsAccordingTo(ordering));

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
                logger.warning(Messages.ImageTagService_warn_unableToCastImageTagsToVersions());
                container.setErrorMsg(Messages.ImageTagService_warn_unableToCastImageTagsToVersions());
            }
        }

        return container;
    }

    private static ResultContainer<AuthService> getAuthService(String registry) {
        ResultContainer<AuthService> container = new ResultContainer<>(new AuthService(AuthType.UNKNOWN));
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
                container.setErrorMsg(Messages.ImageTagService_warn_noAuthServiceAvailableFrom(url));
                logger.warning(Messages.ImageTagService_warn_noAuthServiceAvailableFrom(url));
            }
        } else {
            container.setErrorMsg(Messages.ImageTagService_warn_unknownAuthorizationReceived(type));
            logger.warning(Messages.ImageTagService_warn_unknownAuthorizationReceived(type));
        }

        return container;
    }

    private static ResultContainer<String> getAuthToken(AuthService authService, String image,
                                                        String user, String password) {
        ResultContainer<String> container = new ResultContainer<>("");

        switch (authService.getAuthType()) {
            case BASIC:
                container.setValue(Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
                break;
            case BEARER:
                ResultContainer<String> bearer = getBearerAuthToken(authService, image, user, password);
                bearer.getErrorMsg().ifPresent(container::setErrorMsg);
                container.setValue(bearer.getValue());
                break;
            default:
                container.setErrorMsg(Messages.ImageTagService_warn_authServiceTypeIsUnknown());
                logger.warning(Messages.ImageTagService_warn_authServiceTypeIsUnknown());
        }

        return container;
    }

    private static ResultContainer<String> getBearerAuthToken(AuthService authService, String image,
                                                              String user, String password) {
        ResultContainer<String> container = new ResultContainer<>("");

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        GetRequest request = Unirest.get(authService.getRealm());
        if (!user.isEmpty() && !password.isEmpty()) {
            logger.fine(Messages.ImageTagService_debug_usingBasicAuthenticationToFetchAuthToken());
            request = request.basicAuth(user, password);
        }
        HttpResponse<JsonNode> response = request
            .queryString("service", authService.getService())
            .queryString("scope", "repository:" + image + ":pull")
            .asJson();
        if (response.isSuccess()) {
            ResultContainer<String> token = findTokenInResponse(response, "token", "access_token");
            token.getErrorMsg().ifPresent(container::setErrorMsg);
            container.setValue(token.getValue());
        } else {
            container.setErrorMsg(Messages.ImageTagService_warn_requestFailedNoTokenReceived());
            logger.warning(Messages.ImageTagService_warn_requestFailedNoTokenReceived());
        }
        Unirest.shutDown();

        return container;
    }

    private static ResultContainer<String> findTokenInResponse(HttpResponse<JsonNode> response, String... searchKey) {
        ResultContainer<String> container = new ResultContainer<>("");
        JSONObject jsonObj = response.getBody().getObject();

        for (String key : searchKey) {
            if (jsonObj.has(key)) {
                logger.fine(Messages.ImageTagService_debug_foundTokenInResponse());
                container.setValue(jsonObj.getString(key));
                return container;
            }
        }

        container.setErrorMsg(Messages.ImageTagService_warn_unableToFindTokenInResponse());
        logger.warning(Messages.ImageTagService_warn_unableToFindTokenInResponse());
        return container;
    }

    private static ResultContainer<List<VersionNumber>> getImageTagsFromRegistry(String image, String registry,
                                                                                 AuthType authType, String token) {
        ResultContainer<List<VersionNumber>> container = new ResultContainer<>(new ArrayList<>());
        String url = registry + "/v2/{image}/tags/list";

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        HttpResponse<JsonNode> response = Unirest.get(url)
            .header("Authorization", authType + " " + token)
            .routeParam("image", image)
            .asJson();
        if (response.isSuccess()) {
            logger.fine(Messages.ImageTagService_debug_imageTagRequestSucceededWithStatus(response.getStatus(), response.getStatusText()));
            response.getBody().getObject()
                .getJSONArray("tags")
                .forEach(item -> container.getValue().add(new VersionNumber(item.toString())));
        } else {
            container.setErrorMsg(Messages.ImageTagService_warn_imageTagRequestFailedWithStatus(response.getStatus(), response.getStatusText()));
            logger.warning(Messages.ImageTagService_warn_imageTagRequestFailedWithStatus(response.getStatus(), response.getStatusText()));
        }

        Unirest.shutDown();
        return container;
    }
}