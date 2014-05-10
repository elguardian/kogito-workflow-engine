package org.jbpm.process.test;

import java.util.ArrayList;
import java.util.List;

import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestProcessEventListener implements ProcessEventListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private List<String> eventHistory = new ArrayList<String>();
    
    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
        logAndAdd("bps");
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent event) {
        logAndAdd("aps");
    }

    @Override
    public void beforeProcessCompleted(ProcessCompletedEvent event) {
        logAndAdd("bpc");
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        logAndAdd("apc");
    }

    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
        logAndAdd("bnt-" + ((NodeInstanceImpl) event.getNodeInstance()).getUniqueId());
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        logAndAdd("ant-" + ((NodeInstanceImpl) event.getNodeInstance()).getUniqueId());
    }

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
        logAndAdd("bnl-" + ((NodeInstanceImpl) event.getNodeInstance()).getUniqueId());
    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        logAndAdd("anl-" + ((NodeInstanceImpl) event.getNodeInstance()).getUniqueId());
    }

    @Override
    public void beforeVariableChanged(ProcessVariableChangedEvent event) {
        logAndAdd("bvc-" + event.getVariableId());
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        logAndAdd("avc-" + event.getVariableId());
    }

    public List<String> getEventHistory() { 
        return eventHistory;
    }
    
    private void logAndAdd(String event) { 
        logger.trace(event);
        eventHistory.add(event);
    }
}
