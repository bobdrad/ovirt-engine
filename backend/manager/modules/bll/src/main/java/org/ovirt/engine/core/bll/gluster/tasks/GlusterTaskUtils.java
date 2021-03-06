package org.ovirt.engine.core.bll.gluster.tasks;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ovirt.engine.core.bll.LockMessagesMatchUtil;
import org.ovirt.engine.core.bll.gluster.GlusterTasksSyncJob;
import org.ovirt.engine.core.bll.job.ExecutionContext;
import org.ovirt.engine.core.bll.job.ExecutionHandler;
import org.ovirt.engine.core.bll.job.JobRepository;
import org.ovirt.engine.core.bll.job.JobRepositoryFactory;
import org.ovirt.engine.core.common.asynctasks.gluster.GlusterAsyncTask;
import org.ovirt.engine.core.common.asynctasks.gluster.GlusterTaskParameters;
import org.ovirt.engine.core.common.asynctasks.gluster.GlusterTaskType;
import org.ovirt.engine.core.common.businessentities.VDSGroup;
import org.ovirt.engine.core.common.businessentities.gluster.GlusterTaskSupport;
import org.ovirt.engine.core.common.businessentities.gluster.GlusterVolumeEntity;
import org.ovirt.engine.core.common.constants.gluster.GlusterConstants;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.common.gluster.GlusterFeatureSupported;
import org.ovirt.engine.core.common.job.JobExecutionStatus;
import org.ovirt.engine.core.common.job.Step;
import org.ovirt.engine.core.common.job.StepEnum;
import org.ovirt.engine.core.common.locks.LockingGroup;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.dal.job.ExecutionMessageDirector;
import org.ovirt.engine.core.dao.gluster.GlusterVolumeDao;
import org.ovirt.engine.core.utils.lock.EngineLock;
import org.ovirt.engine.core.utils.lock.LockManager;
import org.ovirt.engine.core.utils.lock.LockManagerFactory;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;

public class GlusterTaskUtils {
    private static GlusterTaskUtils instance;

    private static final String REBALANCE_IN_PROGRESS = "IN PROGRESS";
    private static final String REMOVE_BRICK_FAILED = "MIGRATION FAILED";
    private static final String REMOVE_BRICK_IN_PROGRESS = "MIGRATION IN PROGRESS";
    private static final String REMOVE_BRICK_FINISHED = "MIGRATION COMPLETE";

    private static final Log log = LogFactory.getLog(GlusterTasksSyncJob.class);

    private GlusterTaskUtils() {
    }

    public static GlusterTaskUtils getInstance() {
        if (instance == null) {
            instance = new GlusterTaskUtils();
        }

        return instance;
    }

    public boolean isTaskOfType(GlusterTaskSupport supportObj, GlusterTaskType type) {
        if (supportObj.getAsyncTask() != null && supportObj.getAsyncTask().getType() == type) {
            return true;
        }

        return false;
    }

    public boolean isTaskStatus(GlusterTaskSupport supportObj, JobExecutionStatus status) {
        if (supportObj.getAsyncTask() != null && supportObj.getAsyncTask().getStatus() == status) {
            return true;
        }

        return false;
    }

    /**
     * Releases the lock held on the cluster having given id and locking group {@link LockingGroup#GLUSTER}
     *
     * @param clusterId
     *            ID of the cluster on which the lock is to be released
     */
    public void releaseLock(Guid clusterId) {
        getLockManager().releaseLock(getEngineLock(clusterId));
    }

    /**
     * Returns an {@link EngineLock} instance that represents a lock on a cluster with given id and the locking group
     * {@link LockingGroup#GLUSTER}
     *
     * @param clusterId
     * @return
     */
    private EngineLock getEngineLock(Guid clusterId) {
        return new EngineLock(Collections.singletonMap(clusterId.toString(),
                LockMessagesMatchUtil.makeLockingPair(LockingGroup.GLUSTER,
                        VdcBllMessages.ACTION_TYPE_FAILED_OBJECT_LOCKED)), null);
    }

    public void releaseVolumeLock(Guid taskId) {
        // get volume associated with task
        GlusterVolumeEntity vol = getVolumeDao().getVolumeByGlusterTask(taskId);

        if (vol != null) {
            // release lock on volume
            releaseLock(vol.getId());

        } else {
            log.debugFormat("Did not find a volume associated with task {0}", taskId);
        }
    }

    public void endStepJob(Step step) {
        getJobRepository().updateStep(step);
        ExecutionContext finalContext = ExecutionHandler.createFinalizingContext(step.getId());
        ExecutionHandler.endTaskJob(finalContext, isTaskSuccess(step));
    }

    public boolean isTaskSuccess(Step step) {
        switch (step.getStatus()) {
        case ABORTED:
        case FAILED:
            return false;
        case FINISHED:
            return true;
        default:
            return false;
        }
    }

    public boolean hasTaskCompleted(GlusterAsyncTask task) {
        // Remove brick task is marked completed only if committed or aborted.
        if (JobExecutionStatus.ABORTED == task.getStatus() ||
                (JobExecutionStatus.FINISHED == task.getStatus() && task.getType() != GlusterTaskType.REMOVE_BRICK)
                || JobExecutionStatus.FAILED == task.getStatus()) {
            return true;
        }
        return false;
    }

    public String getTaskMessage(VDSGroup cluster, StepEnum stepType, GlusterAsyncTask task) {
        if (task == null) {
            return null;
        }
        Map<String, String> values = getMessageMap(cluster, task);

        return ExecutionMessageDirector.resolveStepMessage(stepType, values);
    }

    public Map<String, String> getMessageMap(VDSGroup cluster, GlusterAsyncTask task) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(GlusterConstants.CLUSTER, cluster.getName());
        GlusterTaskParameters params = task.getTaskParameters();
        values.put(GlusterConstants.VOLUME, params != null ? params.getVolumeName() : "");
        String jobStatus = getJobStatusInfo(task);
        values.put(GlusterConstants.JOB_STATUS, jobStatus);
        values.put(GlusterConstants.JOB_INFO, task.getMessage());
        return values;
    }

    private String getJobStatusInfo(GlusterAsyncTask task) {
        String jobStatus = task.getStatus().toString();
        if (task.getType() == GlusterTaskType.REMOVE_BRICK) {
            switch (task.getStatus()) {
            case FINISHED:
                jobStatus = REMOVE_BRICK_FINISHED;
                break;
            case STARTED:
                jobStatus = REMOVE_BRICK_IN_PROGRESS;
                break;
            case FAILED:
                jobStatus = REMOVE_BRICK_FAILED;
                break;
            default:
                break;
            }
        }
        if (task.getType() == GlusterTaskType.REBALANCE) {
            switch (task.getStatus()) {
            case STARTED:
                jobStatus = REBALANCE_IN_PROGRESS;
                break;
            default:
                break;
            }
        }
        return jobStatus;
    }

    public void updateSteps(VDSGroup cluster, GlusterAsyncTask task, List<Step> steps) {
        // update status in step table
        for (Step step : steps) {
            if (step.getEndTime() != null) {
                // we have already processed the task
                continue;
            }
            step.setDescription(getTaskMessage(cluster, step.getStepType(), task));
            step.setStatus(task.getStatus());
            if (hasTaskCompleted(task)) {
                step.markStepEnded(task.getStatus());
                endStepJob(step);
                releaseVolumeLock(task.getTaskId());
            } else {
                getJobRepository().updateStep(step);
            }
        }
    }

    public boolean supportsGlusterAsyncTasksFeature(VDSGroup cluster) {
        return cluster.supportsGlusterService()
                && GlusterFeatureSupported.glusterAsyncTasks(cluster.getcompatibility_version());
    }

    public GlusterVolumeDao getVolumeDao() {
        return DbFacade.getInstance().getGlusterVolumeDao();
    }

    public JobRepository getJobRepository() {
        return JobRepositoryFactory.getJobRepository();
    }

    public LockManager getLockManager() {
        return LockManagerFactory.getLockManager();
    }
}
