package io.jenkins.plugins.luxair;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.luxair.logic.ImageTagService;
import io.jenkins.plugins.luxair.model.ErrorContainer;
import io.jenkins.plugins.luxair.model.ImageTag;
import io.jenkins.plugins.luxair.util.StringUtil;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;


public class ImageTagParameterDefinition extends SimpleParameterDefinition {

    private static final long serialVersionUID = 3938123092372L;
    private static final Logger logger = Logger.getLogger(ImageTagParameterDefinition.class.getName());
    private static final ImageTagParameterConfiguration config = ImageTagParameterConfiguration.get();

    private final String image;
    private final String registry;
    private final String filter;
    private final String defaultTag;
    private final String credentialId;
    private final boolean reverseOrder;
    private String errorMsg = "";

    @DataBoundConstructor
    public ImageTagParameterDefinition(String name, String description, String image, String filter, String defaultTag,
                                       String registry, String credentialId, Boolean reverseOrder) {
        super(name, description);
        this.image = image;
        this.registry = StringUtil.isNotNullOrEmpty(registry) ? registry : config.getDefaultRegistry();
        this.filter = StringUtil.isNotNullOrEmpty(filter) ? filter : ".*";
        this.defaultTag = StringUtil.isNotNullOrEmpty(defaultTag) ? defaultTag : "";
        this.credentialId = getDefaultOrEmptyCredentialId(this.registry, credentialId);
        this.reverseOrder = reverseOrder != null && reverseOrder;
    }

    private String getDefaultOrEmptyCredentialId(String registry, String credentialId) {
        if (registry.equals(config.getDefaultRegistry()) && !StringUtil.isNotNullOrEmpty(credentialId)) {
            return config.getDefaultCredentialId();
        } else if (StringUtil.isNotNullOrEmpty(credentialId)) {
            return credentialId;
        } else {
            return "";
        }
    }

    public String getImage() {
        return image;
    }

    public String getRegistry() {
        return registry;
    }

    public String getFilter() {
        return filter;
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public boolean isReverseOrder() {
        return reverseOrder;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public List<ImageTag> getTags() {
        String user = "";
        String password = "";

        StandardUsernamePasswordCredentials credential = findCredential(credentialId);
        if (credential != null) {
            user = credential.getUsername();
            password = credential.getPassword().getPlainText();
        }

        ErrorContainer<List<ImageTag>> imageTags = ImageTagService.getTags(image, registry, filter, user, password, reverseOrder);
        imageTags.getErrorMsg().ifPresent(this::setErrorMsg);
        return imageTags.getValue();
    }

    private StandardUsernamePasswordCredentials findCredential(String credentialId) {
        if (StringUtil.isNotNullOrEmpty(credentialId)) {
            List<Item> items = Jenkins.get().getAllItems();
            for (Item item : items) {
                List<StandardUsernamePasswordCredentials> creds = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    item,
                    ACL.SYSTEM,
                    Collections.emptyList());
                for (StandardUsernamePasswordCredentials cred : creds) {
                    if (cred.getId().equals(credentialId)) {
                        return cred;
                    }
                }
            }
            logger.warning("Cannot find credential for :" + credentialId + ":");
        } else {
            logger.info("CredentialId is empty");
        }
        return null;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof ImageTagParameterValue) {
            ImageTagParameterValue value = (ImageTagParameterValue) defaultValue;
            return new ImageTagParameterDefinition(getName(), getDescription(),
                getImage(), getFilter(), value.getImageTag(),
                getRegistry(), getCredentialId(), isReverseOrder());
        }
        return this;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new ImageTagParameterValue(getName(), image, value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(ImageTagParameterValue.class, jo);
    }

    @Symbol("imageTag")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Image Tag Parameter";
        }

        @SuppressWarnings("unused")
        public String defaultRegistry() {
            return config.getDefaultRegistry();
        }

        @SuppressWarnings("unused")
        public String defaultCredentialID() {
            return config.getDefaultCredentialId();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item context,
                                                    @QueryParameter String credentialId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                logger.info("No permission to list credential in context: " + context);
                return new StandardListBoxModel().includeCurrentValue(credentialId);
            }
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, context, StandardUsernameCredentials.class)
                .includeCurrentValue(credentialId);
        }
    }
}