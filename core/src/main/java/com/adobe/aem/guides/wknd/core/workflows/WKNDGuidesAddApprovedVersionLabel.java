package com.adobe.aem.guides.wknd.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=WKND Guides Add Approved Version Label when Document State is Approved"
        }
)

public class WKNDGuidesAddApprovedVersionLabel implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(WKNDGuidesAddApprovedVersionLabel.class);
    private static final String APPROVED_VERSION_LABEL = "Approved";

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
            throws WorkflowException {

        String payloadPath = workItem.getWorkflowData().getPayload().toString();
        String assetPath = payloadPath.endsWith("/jcr:content/metadata")
                ? payloadPath.substring(0, payloadPath.length() - "/jcr:content/metadata".length())
                : payloadPath;

        LOG.info("Processing asset for version labeling: {}", assetPath);

        try {
            ResourceResolver resolver = workflowSession.adaptTo(ResourceResolver.class);
            if (resolver == null) {
                throw new WorkflowException("ResourceResolver is null");
            }

            Resource assetResource = resolver.getResource(assetPath);
            if (assetResource == null) {
                LOG.warn("Asset not found: {}", assetPath);
                return;
            }

            Resource metadata = assetResource.getChild("jcr:content/metadata");
            if (metadata == null) {
                LOG.warn("Metadata node missing for asset: {}", assetPath);
                return;
            }

            String docState = metadata.getValueMap().get("docstate", String.class);

            if (docState == null) {
                LOG.warn("docstate metadata missing for asset: {}", assetPath);
                return;
            }

            if (!APPROVED_VERSION_LABEL.equalsIgnoreCase(docState)) {
                LOG.info("Skipping asset {}, docstate is '{}'", assetPath, docState);
                return;
            }

            LOG.info("Asset {} is in 'Approved' state, adding version label", assetPath);
            addVersionLabel(assetResource, resolver);


        } catch (Exception e) {
            LOG.error("Failed to add version label for asset: {}", assetPath, e);
            throw new WorkflowException(e);
        }
    }

    private void addVersionLabel(Resource assetResource, ResourceResolver resolver) throws Exception {

        Session session = resolver.adaptTo(Session.class);
        Node assetNode = assetResource.adaptTo(Node.class);

        if (session == null || assetNode == null) {
            LOG.error("Session or asset node is null for {}", assetResource.getPath());
            return;
        }

        VersionManager versionManager = session.getWorkspace().getVersionManager();

        if (!assetNode.isNodeType("mix:versionable")) {
            assetNode.addMixin("mix:versionable");
            session.save();
            LOG.info("Added mix:versionable to {}", assetResource.getPath());
        }

        Version version = versionManager.checkin(assetNode.getPath());

        VersionHistory versionHistory = versionManager.getVersionHistory(assetNode.getPath());
        versionHistory.addVersionLabel(version.getName(), APPROVED_VERSION_LABEL, true);

        versionManager.checkout(assetNode.getPath());

        session.save();

        LOG.info("Version label '{}' added to {}", APPROVED_VERSION_LABEL, assetResource.getPath());
    }
}
