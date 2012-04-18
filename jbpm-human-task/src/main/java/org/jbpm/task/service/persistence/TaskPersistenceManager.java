package org.jbpm.task.service.persistence;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.jbpm.task.Group;
import org.jbpm.task.Status;
import org.jbpm.task.Task;
import org.jbpm.task.User;
import org.jbpm.task.query.DeadlineSummary;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.persistence.TaskTransactionManager.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * From the Hibernate docs: 
 * </p>
 * <pre>
 * An EntityManager is an inexpensive, non-threadsafe object that 
 * should be used once, for a single business process, a single unit 
 * of work, and then discarded. An EntityManager will not obtain 
 * a JDBC Connection (or a Datasource) unless it is needed, so 
 * you may safely open and close an EntityManager even if you are 
 * not sure that data access will be needed to serve a particular request.
 * </pre>
 * </p>
 * 
 * This class is a wrapper around the entity manager that handles 
 * all persistence operations. This way, the persistence functionality
 * can be isolated from the human-task server functionality. 
 * </p>
 * 
 * This class is only mean to be used in one thread: with every request
 * handled by the human-task server, a TaskServiceSession is created
 * with an instance of this class. Once the request has been handled, 
 * the TaskServiceSession instance and the TaskPersistenceManager
 * instance are disposed of. 
 * </p>
 */
public class TaskPersistenceManager {

    private Logger logger = LoggerFactory.getLogger(getClass());
    
    private TaskTransactionManager ttxm;
    private EntityManager em;

    static { 
        TaskServiceSession.setTaskPersistenceManagerFactory(new TaskPersistenceManagerFactory());
        TaskService.setTaskPersistenceManagerFactory(new TaskPersistenceManagerFactory());
    }
    
    TaskPersistenceManager(EntityManagerFactory entityManagerFactory) { 
        this.ttxm = TaskTransactionManager.getInstance(entityManagerFactory);
        this.em = entityManagerFactory.createEntityManager();
    }

    //=====
    // dealing with transactions
    //=====
    
    public boolean beginTransaction() { 
        boolean txOwner = this.ttxm.ownsTransaction(em);
        if( txOwner ) {  
            this.ttxm.begin(em);
        }
        this.ttxm.attachPersistenceContext(em);
        return txOwner;
    }

    /**
     * This method attempts to end a transaction -- if it's called
     * by someone/thing claiming to be the transaction owner 
     * (otherwise known as the thing that started the transaction). 
     * </p>
     * If we're the tx owner, we first check if the tx has been 
     * marked for rollback. If it's marked for rollback, it doesn't 
     * make sense to commit since committing will only throw an exception. 
     * Instead, we rollback the tx. 
     * </p>
     * If it's <i>not</i> marked for rollback, we commit the tx. 
     * </p>
     * Unfortunately, rolling back or committing might have caused 
     * an exception. If we haven't yet tried to rollback, then 
     * we do it at this point: committing didn't work and we don't want 
     * to leave the transaction open. 
     * </p>
     * While we could technically add more code to make sure that
     * the transaction is closed (for example, a finally clause that
     * checks if the tx is active, etc), that's overkill because
     * the code below does everything possible to close the transaction. 
     * </p>
     * Anything done in a finally clause would either being confusing
     * (for example, checking if the tx is active <i>after</i> the 
     * tx has been commited successfully) or simply retry what
     * had already been done (rollback when that had already been 
     * tried in the catch clause). 
     * </p>
     * @param em The EntityManager: neccessary for local/entity transactions.
     * @param txOwner Whether or not the caller started this transaction.
     */
    public void endTransaction(boolean txOwner) { 
        if( txOwner ) { 
            boolean rollbackAttempted = false;
            try { 
                if( ttxm.getStatus(em) == TransactionStatus.MARKED_ROLLBACK) { 
                    rollbackAttempted = true;
                    ttxm.rollback(em, txOwner);
                }
                this.ttxm.commit(em);
            } catch(RuntimeException re) { 
                String action = rollbackAttempted ? "rollback" : "commit";
                logger.error("Unable to " + action + ".", re);

                if( ! rollbackAttempted ) { 
                    this.ttxm.rollback(em, txOwner);
                }
                
                throw re;
                
            }
        }
    }
    
    public void rollBackTransaction(boolean txOwner) { 
        try { 
            if( ttxm.getStatus(em) == TransactionStatus.ACTIVE ) { 
                this.ttxm.rollback(em, txOwner);
            }
        } catch(RuntimeException e) { 
            logger.error("Unable to (mark as or) rollback transaction!", e.getCause());
       
        }
        
    }
    
    public void endPersistenceContext() { 
        if( em == null ) { 
            ttxm = null;
            return;
        }
        
        boolean closeEm = em.isOpen();
        if ( closeEm  ) { 
            try { 
                if( em.getTransaction().isActive() ) {
                    endTransaction(true);
                }
                em.close();
            }
            catch( Exception e ) { 
                // Don't worry about it, we're cleaning up. 
            }
        }
        
        this.em = null;
        this.ttxm = null;
    }

    //=====
    // onetime methods
    //=====
    
    /**
     * Special onetime method
     * @return
     */
    public List<DeadlineSummary> getUnescalatedDeadlines() { 
        boolean txOwner = beginTransaction();
        
        List<DeadlineSummary> resultList = getUnescalatedDeadlinesList();
        
        endTransaction(txOwner);
        return resultList;
    }

    /**
     *  Special onetime method
     * @param taskId
     * @param taskStatus
     */
    public void setTaskStatusInTransaction(final Object taskId, Status taskStatus) { 
        boolean txOwner = beginTransaction();
        
        Task task = (Task) em.find(Task.class, taskId);
        task.getTaskData().setStatus(Status.Completed);
        
        em.persist(task);
        endTransaction(txOwner);
    }
    
    
    //=====
    // In session methods
    //=====
    
    @SuppressWarnings("unchecked")
    public List<DeadlineSummary> getUnescalatedDeadlinesList() { 
        return em.createNamedQuery("UnescalatedDeadlines").getResultList();
    }
    
    public Object findEntity(Class<?> entityClass, Object primaryKey) { 
        return this.em.find(entityClass, primaryKey);
    }
    
    public void deleteEntity(Object entity) { 
        em.remove(entity);
    }
    
    public void saveEntity(Object entity) { 
        em.persist(entity);
    }
    
    public Query createQuery(String queryName) { 
        return em.createNamedQuery(queryName);
    }
    
    public Query createNewQuery(String queryString ) { 
        return em.createQuery(queryString);
    }
    
    public boolean userExists(String userId) { 
        if( em.find(User.class, userId) == null ) { 
            return false;
        }
        return true;
    }
    
    public boolean groupExists(String groupId) { 
        if( em.find(Group.class, groupId) == null ) { 
            return false;
        }
        return true;
    }
    
    public List<TaskSummary> queryTasksWithUserIdAndLanguage(String queryName, String userId, String language) { 
        Query query = createQuery(queryName);
        query.setParameter("userId", userId);
        query.setParameter("language", language);
        Object resultListObject = query.getResultList();

        return (List<TaskSummary>) resultListObject;
    }
    public List<TaskSummary> queryTasksWithUserIdGroupsAndLanguage(String queryName, String userId, List<String> groupIds, String language) { 
        Query query = createQuery(queryName);
        query.setParameter("userId", userId);
        query.setParameter("groupIds", groupIds);
        query.setParameter("language", language);
        Object resultListObject = query.getResultList();

        return (List<TaskSummary>) resultListObject;
    }


 
}