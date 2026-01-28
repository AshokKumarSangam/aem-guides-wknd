package com.adobe.aem.guides.wknd.core.workflows;


import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=WKND Guides Output Generated Path for AEM Guides Outputs"
        }
)

public class WKNDGuidesOutputGeneratedPath implements WorkflowProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(WKNDGuidesOutputGeneratedPath.class);


    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {

        String payloadPath = workItem.getWorkflowData().getPayload().toString();
        LOGGER.info("** Workflow payload (DITAMAP): {}", payloadPath);

        String outputGeneratedPath = workItem.getWorkflowData().getMetaDataMap().get("generatedPath", String.class);
        LOGGER.info("** Workflow payload Target path (DITAMAP): {}", outputGeneratedPath);

        String outputPath = outputGeneratedPath.contains(".") ? outputGeneratedPath.substring(0, outputGeneratedPath.lastIndexOf('.')) : outputGeneratedPath;
        LOGGER.info("** Workflow Output path (DITAMAP): {}", outputPath);


    }
}
