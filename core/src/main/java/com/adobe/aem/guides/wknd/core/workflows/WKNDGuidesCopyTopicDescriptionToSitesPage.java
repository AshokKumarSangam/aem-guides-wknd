package com.adobe.aem.guides.wknd.core.workflows;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=WKND Guides Copy Topic Description to Sites Generated Page"
        }
)
public class WKNDGuidesCopyTopicDescriptionToSitesPage implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(WKNDGuidesCopyTopicDescriptionToSitesPage.class);

    private static final String AEMSITES_RELATIVE_PATH =
            "/jcr:content/metadata/namedoutputs/aemsites";

    private static final String TARGET_PATH_PROP = "fmdita-targetPath";
    private static final String SOURCE_PATH_PROP = "sourcePath";
    private static final String DC_DESCRIPTION = "dc:description";

    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {

        ResourceResolver resolver = session.adaptTo(ResourceResolver.class);
        if (resolver == null) {
            LOG.error("Failed to adapt WorkflowSession to ResourceResolver");
            return;
        }

        String payloadPath = item.getWorkflowData().getPayload().toString();
        LOG.info("** Workflow payload (DITAMAP): {}", payloadPath);

        Resource aemSitesNode = resolver.getResource(payloadPath + AEMSITES_RELATIVE_PATH);
        LOG.info("** AEM Sites namedoutput node: {}", aemSitesNode);

        if (aemSitesNode == null) {
            LOG.error("AEM Sites namedoutput node not found under payload");
            return;
        }

        String basePath = aemSitesNode.getValueMap().get(TARGET_PATH_PROP, String.class);

        if (basePath == null || basePath.isBlank()) {
            LOG.error("fmdita-targetPath not found on aemsites node");
            return;
        }

        LOG.info("** Using extracted AEM Sites target path as BASE_PATH: {}", basePath);

        // Traverse generated AEM Sites pages
        Resource baseResource = resolver.getResource(basePath);
        if (baseResource == null) {
            LOG.error("Generated AEM Sites base path not found: {}", basePath);
            return;
        }

        traversePages(baseResource, resolver);

        try {
            resolver.commit();
            LOG.info("Workflow completed successfully");
        } catch (Exception e) {
            LOG.error("Failed to commit changes", e);
            throw new WorkflowException(e);
        }
    }

    // Recursive traversal of AEM Sites pages
    private void traversePages(Resource resource, ResourceResolver resolver) {

        Resource content = resource.getChild("jcr:content");
        if (content != null) {

            ValueMap props = content.getValueMap();
            String title = props.get("jcr:title", resource.getName());
            String sourcePath = props.get(SOURCE_PATH_PROP, String.class);

            LOG.info("--------------------------------------------------");
            LOG.info("Page Path : {}", resource.getPath());
            LOG.info("Page Title: {}", title);
            LOG.info("SourcePath: {}", sourcePath);

            if (sourcePath != null && sourcePath.endsWith(".dita")) {

                String metadataPath = sourcePath + "/jcr:content/metadata";
                Resource topicMetadata = resolver.getResource(metadataPath);

                if (topicMetadata != null) {

                    String dcDescription = topicMetadata.getValueMap().get(DC_DESCRIPTION, String.class);

                    if (dcDescription != null && !dcDescription.isBlank()) {

                        ModifiableValueMap modProps = content.adaptTo(ModifiableValueMap.class);

                        if (modProps != null) {
                            modProps.put("jcr:description", dcDescription);
                            LOG.info("Updated jcr:description on page with description: {}", dcDescription);
                        }

                    } else {
                        LOG.debug("dc:description empty for topic: {}", sourcePath);
                    }

                } else {
                    LOG.warn("Topic metadata node not found: {}", metadataPath);
                }
            }
        }

        for (Resource child : resource.getChildren()) {
            traversePages(child, resolver);
        }
    }
}
