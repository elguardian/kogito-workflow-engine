/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jbpm.services.task.internals.lifecycle;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.jboss.seam.transaction.Transactional;
import org.jbpm.services.task.annotations.Internal;
import org.jbpm.services.task.annotations.Mvel;
import org.jbpm.services.task.events.AfterTaskActivatedEvent;
import org.jbpm.services.task.events.AfterTaskClaimedEvent;
import org.jbpm.services.task.events.AfterTaskCompletedEvent;
import org.jbpm.services.task.events.AfterTaskDelegatedEvent;
import org.jbpm.services.task.events.AfterTaskExitedEvent;
import org.jbpm.services.task.events.AfterTaskFailedEvent;
import org.jbpm.services.task.events.AfterTaskForwardedEvent;
import org.jbpm.services.task.events.AfterTaskReleasedEvent;
import org.jbpm.services.task.events.AfterTaskResumedEvent;
import org.jbpm.services.task.events.AfterTaskSkippedEvent;
import org.jbpm.services.task.events.AfterTaskStartedEvent;
import org.jbpm.services.task.events.AfterTaskStoppedEvent;
import org.jbpm.services.task.events.AfterTaskSuspendedEvent;
import org.jbpm.services.task.events.BeforeTaskActivatedEvent;
import org.jbpm.services.task.events.BeforeTaskClaimedEvent;
import org.jbpm.services.task.events.BeforeTaskCompletedEvent;
import org.jbpm.services.task.events.BeforeTaskDelegatedEvent;
import org.jbpm.services.task.events.BeforeTaskExitedEvent;
import org.jbpm.services.task.events.BeforeTaskFailedEvent;
import org.jbpm.services.task.events.BeforeTaskForwardedEvent;
import org.jbpm.services.task.events.BeforeTaskReleasedEvent;
import org.jbpm.services.task.events.BeforeTaskResumedEvent;
import org.jbpm.services.task.events.BeforeTaskSkippedEvent;
import org.jbpm.services.task.events.BeforeTaskStartedEvent;
import org.jbpm.services.task.events.BeforeTaskStoppedEvent;
import org.jbpm.services.task.events.BeforeTaskSuspendedEvent;
import org.jbpm.services.task.exception.PermissionDeniedException;
import org.jbpm.services.task.exception.TaskException;
import org.jbpm.services.task.impl.model.ContentImpl;
import org.jbpm.services.task.impl.model.FaultDataImpl;
import org.jbpm.services.task.impl.model.TaskImpl;
import org.jbpm.services.task.impl.model.UserImpl;
import org.jbpm.services.task.lifecycle.listeners.TaskLifeCycleEventListener;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.jbpm.shared.services.api.JbpmServicesPersistenceManager;
import org.kie.api.task.model.Group;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.PeopleAssignments;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskData;
import org.kie.api.task.model.User;
import org.kie.internal.task.api.TaskContentService;
import org.kie.internal.task.api.TaskIdentityService;
import org.kie.internal.task.api.TaskQueryService;
import org.kie.internal.task.api.model.InternalPeopleAssignments;
import org.kie.internal.task.api.model.InternalTaskData;
import org.kie.internal.task.api.model.Operation;
import org.mvel2.MVEL;
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 */
@Mvel
@ApplicationScoped
@Transactional
public class MVELLifeCycleManager implements LifeCycleManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MVELLifeCycleManager.class);
    
    @Inject 
    private JbpmServicesPersistenceManager pm;
    @Inject
    private TaskQueryService taskQueryService;
    @Inject
    private TaskIdentityService taskIdentityService;
    @Inject
    private TaskContentService taskContentService;
    @Inject
    private Event<Task> taskEvents;
    @Inject
    @Internal
    private TaskLifeCycleEventListener eventListener;
    private Map<Operation, List<OperationCommand>> operations;

    public MVELLifeCycleManager() {
    }

    public void setPm(JbpmServicesPersistenceManager pm) {
        this.pm = pm;
    }
    
    public void setTaskEvents(Event<Task> taskEvents) {
        this.taskEvents = taskEvents;
    }
    
    public void setTaskQueryService(TaskQueryService taskQueryService) {
        this.taskQueryService = taskQueryService;
    }

    public void setTaskIdentityService(TaskIdentityService taskIdentityService) {
        this.taskIdentityService = taskIdentityService;
    }

    public void setTaskContentService(TaskContentService taskContentService) {
        this.taskContentService = taskContentService;
    }
    

    void evalCommand(final Operation operation, final List<OperationCommand> commands, final Task task,
            final User user, final OrganizationalEntity targetEntity,
            List<String> groupIds) throws PermissionDeniedException {

        boolean statusMatched = false;
        final TaskData taskData = task.getTaskData();
        for (OperationCommand command : commands) {
            // first find out if we have a matching status
            if (command.getStatus() != null) {
                for (Status status : command.getStatus()) {
                    if (task.getTaskData().getStatus() == status) {
                        statusMatched = true;
                        // next find out if the user can execute this doOperation
                        if (!isAllowed(command, task, user, groupIds)) {
                            String errorMessage = "User '" + user + "' does not have permissions to execution operation '" + operation + "' on task id " + task.getId();

                            throw new PermissionDeniedException(errorMessage);
                        }

                        commands(command, task, user, targetEntity);
                    } else {
                        logger.debug("No match on status for task {} :status {}  != {}", task.getId(), task.getTaskData().getStatus(), status);
                    }
                }
            }

            if (command.getPreviousStatus() != null) {
                for (Status status : command.getPreviousStatus()) {
                    if (taskData.getPreviousStatus() == status) {
                        statusMatched = true;

                        // next find out if the user can execute this doOperation
                        if (!isAllowed(command, task, user, groupIds)) {
                            String errorMessage = "User '" + user + "' does not have permissions to execution operation '" + operation + "' on task id " + task.getId();
                            throw new PermissionDeniedException(errorMessage);
                        }

                        commands(command, task, user, targetEntity);
                    } else {
                        logger.debug("No match on previous status for task {} :status {}  != {}", task.getId(), task.getTaskData().getStatus(), status);
                    }
                }
            }
        }
        if (!statusMatched) {
            String errorMessage = "User '" + user + "' was unable to execution operation '" + operation + "' on task id " + task.getId() + " due to a no 'current status' match";
            throw new PermissionDeniedException(errorMessage);
        }
    }

    private boolean isAllowed(final OperationCommand command, final Task task, final User user,
            List<String> groupIds) {


        boolean operationAllowed = false;
        for (Allowed allowed : command.getAllowed()) {
            if (operationAllowed) {
                break;
            }
            switch (allowed) {
                case Owner: {
                    operationAllowed = (task.getTaskData().getActualOwner() != null && task.getTaskData().getActualOwner().equals(user));
                    break;
                }
                case Initiator: {
                    operationAllowed = (task.getTaskData().getCreatedBy() != null
                            && (task.getTaskData().getCreatedBy().equals(user))
                            || (groupIds != null && groupIds.contains(task.getTaskData().getCreatedBy().getId())));
                    break;
                }
                case PotentialOwner: {
                    operationAllowed = isAllowed(user, groupIds, (List<OrganizationalEntity>) task.getPeopleAssignments().getPotentialOwners());
                    break;
                }
                case BusinessAdministrator: {
                    operationAllowed = isAllowed(user, groupIds, (List<OrganizationalEntity>) task.getPeopleAssignments().getBusinessAdministrators());
                    break;
                }
                case Anyone: {
                    operationAllowed = true;
                    break;
                }
            }
        }

        if (operationAllowed && command.isUserIsExplicitPotentialOwner()) {
            // if user has rights to execute the command, make sure user is explicitly specified (not as a group)
            operationAllowed = task.getPeopleAssignments().getPotentialOwners().contains(user);
        }

        if (operationAllowed && command.isSkipable()) {
            operationAllowed = task.getTaskData().isSkipable();
        }

        return operationAllowed;
    }

    private boolean isAllowed(final User user, final List<String> groupIds, final List<OrganizationalEntity> entities) {
        // for now just do a contains, I'll figure out group membership later.
        for (OrganizationalEntity entity : entities) {
            if (entity instanceof User && entity.equals(user)) {
                return true;
            }
            if (entity instanceof Group && groupIds != null && groupIds.contains(entity.getId())) {
                return true;
            }
        }
        return false;
    }

    private void commands(final OperationCommand command, final Task task, final User user,
            final OrganizationalEntity targetEntity) {


        final PeopleAssignments people = task.getPeopleAssignments();
        final InternalTaskData taskData = (InternalTaskData) task.getTaskData();

        if (command.getNewStatus() != null) {
            taskData.setStatus(command.getNewStatus());
        } else if (command.isSetToPreviousStatus()) {
            taskData.setStatus(taskData.getPreviousStatus());
        }

        if (command.isAddTargetEntityToPotentialOwners() && !people.getPotentialOwners().contains(targetEntity)) {
            people.getPotentialOwners().add(targetEntity);
        }

        if (command.isRemoveUserFromPotentialOwners()) {
            people.getPotentialOwners().remove(user);
        }

        if (command.isSetNewOwnerToUser()) {
            taskData.setActualOwner(user);
        }

        if (command.isSetNewOwnerToNull()) {
            taskData.setActualOwner(null);
        }

        if (command.getExec() != null) {
            switch (command.getExec()) {
                case Claim: {
                    taskData.setActualOwner((UserImpl) targetEntity);
                    // @TODO: Ical stuff
                    // Task was reserved so owner should get icals
//                    SendIcal.getInstance().sendIcalForTask(task, service.getUserinfo());

                    break;
                }
            }
        }


    }

    public void taskOperation(final Operation operation, final long taskId, final String userId,
            final String targetEntityId, final Map<String, Object> data,
            List<String> groupIds) throws TaskException {

        try {
            final List<OperationCommand> commands = operations.get(operation);

            Task task = taskQueryService.getTaskInstanceById(taskId);
            User user = taskIdentityService.getUserById(userId);
            OrganizationalEntity targetEntity = null;
            if (targetEntityId != null && !targetEntityId.equals("")) {
                targetEntity = taskIdentityService.getOrganizationalEntityById(targetEntityId);
            }

            switch (operation) {    
                case Activate: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskActivatedEvent>() {
                    }).fire(task);
                    break;
                }
                case Claim: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskClaimedEvent>() {
                    }).fire(task);
                    break;
                }
                case Complete: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskCompletedEvent>() {
                    }).fire(task);
                    break;
                }
                case Delegate: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskDelegatedEvent>() {
                    }).fire(task);
                    break;
                }
                case Exit: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskExitedEvent>() {
                    }).fire(task);
                    break;
                }

                case Fail: {
                    if (data != null) {

                        FaultDataImpl faultData = ContentMarshallerHelper.marshalFault(data, null);
                        ContentImpl content = new ContentImpl();
                        content.setContent(faultData.getContent());
                        pm.persist(content);
                        ((InternalTaskData) task.getTaskData()).setFault(content.getId(), faultData);


                    }
                    taskEvents.select(new AnnotationLiteral<BeforeTaskFailedEvent>() {
                    }).fire(task);
                    break;
                }
                case Forward: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskForwardedEvent>() {
                    }).fire(task);
                    break;
                }
                case Release: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskReleasedEvent>() {
                    }).fire(task);
                    break;
                }
                case Resume: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskResumedEvent>() {
                    }).fire(task);
                    break;
                }
                case Skip: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskSkippedEvent>() {
                    }).fire(task);
                    break;
                }
                case Start: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskStartedEvent>() {
                    }).fire(task);
                    break;
                }
                case Stop: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskStoppedEvent>() {
                    }).fire(task);
                    break;
                }
                case Suspend: {
                    taskEvents.select(new AnnotationLiteral<BeforeTaskSuspendedEvent>() {
                    }).fire(task);
                    break;
                }

            }
            
            evalCommand(operation, commands, task, user, targetEntity, groupIds);

            switch (operation) {
                case Activate: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskActivatedEvent>() {
                    }).fire(task);
                    break;
                }
                case Claim: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskClaimedEvent>() {
                    }).fire(task);
                    break;
                }
                case Complete: {
                    if (data != null) {
                        
                        taskContentService.addContent(taskId, data);
                        
                    }

                    taskEvents.select(new AnnotationLiteral<AfterTaskCompletedEvent>() {
                    }).fire(task);
                    break;
                }
                case Delegate: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskDelegatedEvent>() {
                    }).fire(task);
                    // This is a really bad hack to execut the correct behavior
                    ((InternalTaskData) task.getTaskData()).setStatus(Status.Reserved);
                }
                case Exit: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskExitedEvent>() {
                    }).fire(task);
                    break;
                }
                case Fail: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskFailedEvent>() {
                    }).fire(task);
                    break;
                }
                case Forward: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskForwardedEvent>() {
                    }).fire(task);
                    break;
                }   
                case Release: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskReleasedEvent>() {
                    }).fire(task);
                    break;
                }
                case Resume: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskResumedEvent>() {
                    }).fire(task);
                    break;
                }
                case Start: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskStartedEvent>() {
                    }).fire(task);
                    break;
                }
                case Skip: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskSkippedEvent>() {
                    }).fire(task);
                    break;
                }
                case Stop: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskStoppedEvent>() {
                    }).fire(task);
                    break;
                }    
                case Suspend: {
                    taskEvents.select(new AnnotationLiteral<AfterTaskSuspendedEvent>() {
                    }).fire(task);
                    break;
                }
            }
        } catch (RuntimeException re) {
            throw re;
        }


    }

    @PostConstruct
    public void initMVELOperations() {

        Map<String, Object> vars = new HashMap<String, Object>();

        // Search operations-dsl.mvel, if necessary using superclass if TaskService is subclassed
        InputStream is = null;
        // for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
        is = getClass().getResourceAsStream("/operations-dsl.mvel");
//            if (is != null) {
//                break;
//            }
        //}
        if (is == null) {
            throw new RuntimeException("Unable To initialise TaskService, could not find Operations DSL");
        }
        Reader reader = new InputStreamReader(is);
        try {
            operations = (Map<Operation, List<OperationCommand>>) eval(toString(reader), vars);
        } catch (IOException e) {
            throw new RuntimeException("Unable To initialise TaskService, could not load Operations DSL");
        }


    }

    public static String toString(Reader reader) throws IOException {
        int charValue;
        StringBuffer sb = new StringBuffer(1024);
        while ((charValue = reader.read()) != -1) {
            sb.append((char) charValue);
        }
        return sb.toString();
    }

    public static Object eval(Reader reader) {
        try {
            return eval(toString(reader), null);
        } catch (IOException e) {
            throw new RuntimeException("Exception Thrown", e);
        }
    }

    public static Object eval(Reader reader, Map<String, Object> vars) {
        try {
            return eval(toString(reader), vars);
        } catch (IOException e) {
            throw new RuntimeException("Exception Thrown", e);
        }
    }

    public static Object eval(String str, Map<String, Object> vars) {
        ParserConfiguration pconf = new ParserConfiguration();
        pconf.addPackageImport("org.kie.internal.task.api.model");
        pconf.addPackageImport("org.jbpm.services.task");
        pconf.addPackageImport("org.jbpm.services.task.impl.model");
        pconf.addPackageImport("org.jbpm.services.task.query");
        pconf.addPackageImport("org.jbpm.services.task.internals.lifecycle");

        pconf.addImport(Status.class);
        pconf.addImport(Allowed.class);
        pconf.addPackageImport("java.util");

        ParserContext context = new ParserContext(pconf);
        Serializable s = MVEL.compileExpression(str.trim(), context);

        if (vars != null) {
            return MVEL.executeExpression(s, vars);
        } else {
            return MVEL.executeExpression(s);
        }
    }

    public void nominate(long taskId, String userId, List<OrganizationalEntity> potentialOwners) {
        final TaskImpl task = pm.find(TaskImpl.class, taskId);
        final UserImpl user = pm.find(UserImpl.class, userId);
        if (isAllowed(user, null, (List<OrganizationalEntity>) task.getPeopleAssignments().getBusinessAdministrators())) {


            ((InternalTaskData) task.getTaskData()).assignOwnerAndStatus(potentialOwners);
            if (task.getTaskData().getStatus() == Status.Ready) {
                ((InternalPeopleAssignments) task.getPeopleAssignments()).setPotentialOwners(potentialOwners);
            }

        } else {
            throw new PermissionDeniedException("User " + userId + " is not allowed to perform Nominate on Task " + taskId);
        }
    }
}
