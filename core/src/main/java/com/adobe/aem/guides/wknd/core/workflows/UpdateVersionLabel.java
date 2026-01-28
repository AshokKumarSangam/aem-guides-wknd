package com.adobe.aem.guides.wknd.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.dam.api.Asset;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;


@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=XXX Update Version Label Process"
        }
)
public class UpdateVersionLabel implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateVersionLabel.class);
    private static final String VERSION_LABEL = "Approved";

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
            throws WorkflowException {

        LOG.info("Starting ApprovedToPublish workflow process");

        try {
            WorkflowData workflowData = workItem.getWorkflowData();
            String payloadPath = workflowData.getPayload().toString();

            String suffix = "/jcr:content/metadata";
            if (payloadPath.endsWith(suffix)) {
                payloadPath = payloadPath.substring(0, payloadPath.length() - suffix.length());
            }


            LOG.info("Processing asset at path: {}", payloadPath);

            ResourceResolver resourceResolver = workflowSession.adaptTo(ResourceResolver.class);
            if (resourceResolver == null) {
                throw new WorkflowException("Unable to get ResourceResolver from WorkflowSession");
            }

            Resource assetResource = resourceResolver.getResource(payloadPath);
            if (assetResource == null) {
                LOG.warn("Asset resource not found at path: {}", payloadPath);
                return;
            }

            Asset asset = assetResource.adaptTo(Asset.class);
            if (asset == null) {
                LOG.warn("Resource is not a DAM asset: {}", payloadPath);
                return;
            }

            Resource metadataResource = null != assetResource.getChild("jcr:content") ? assetResource.getChild("jcr:content").getChild("metadata") : null;
            if (metadataResource != null) {
                String docstate = metadataResource.getValueMap().get("docstate", String.class);

                if ("Approved".equals(docstate)) {
                    LOG.info("Asset docstate is 'Published', adding version label");
                    addVersionLabel(assetResource, resourceResolver);
                } else {
                    LOG.info("Asset docstate is '{}', not 'Published'. Skipping version labeling.", docstate);
                }
            } else {
                LOG.warn("Metadata resource not found for asset: {} ", payloadPath);
            }

        } catch (Exception e) {
            LOG.error("Error in ApprovedToPublish workflow process", e);
            throw new WorkflowException("Error processing workflow", e);
        }

        LOG.info("Completed ApprovedToPublish workflow process");
    }

    private void addVersionLabel(Resource assetResource, ResourceResolver resourceResolver)
            throws RepositoryException {

        Session session = resourceResolver.adaptTo(Session.class);
        if (session == null) {
            throw new RepositoryException("Unable to get JCR Session");
        }

        Node assetNode = assetResource.adaptTo(Node.class);
        if (assetNode == null) {
            throw new RepositoryException("Unable to adapt resource to Node");
        }

        try {
            VersionManager versionManager = session.getWorkspace().getVersionManager();

            if (!assetNode.isNodeType("mix:versionable")) {
                assetNode.addMixin("mix:versionable");
                session.save();
            }

            Version version = versionManager.checkin(assetNode.getPath());

            VersionHistory versionHistory = versionManager.getVersionHistory(assetNode.getPath());
            versionHistory.addVersionLabel(version.getName(), VERSION_LABEL, true);

            versionManager.checkout(assetNode.getPath());

            session.save();

            LOG.info("Successfully added version label '{}' to asset: {}", VERSION_LABEL, assetResource.getPath());

        } catch (RepositoryException e) {
            LOG.error("Error adding version label to asset: {}", assetResource.getPath(), e);
            throw e;
        }
    }

}
