package org.ovirt.engine.core.bll;

import static org.ovirt.engine.core.common.config.ConfigValues.VdsRefreshRate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.core.bll.job.ExecutionContext;
import org.ovirt.engine.core.bll.job.ExecutionContext.ExecutionMethod;
import org.ovirt.engine.core.bll.job.ExecutionHandler;
import org.ovirt.engine.core.bll.job.JobRepositoryFactory;
import org.ovirt.engine.core.bll.scheduling.RunVmDelayer;
import org.ovirt.engine.core.bll.snapshots.SnapshotsValidator;
import org.ovirt.engine.core.bll.storage.StorageHelperDirector;
import org.ovirt.engine.core.common.action.RemoveVmHibernationVolumesParameters;
import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.action.VdcReturnValueBase;
import org.ovirt.engine.core.common.action.VmOperationParameterBase;
import org.ovirt.engine.core.common.businessentities.IVdsAsyncCommand;
import org.ovirt.engine.core.common.businessentities.LUNs;
import org.ovirt.engine.core.common.businessentities.LunDisk;
import org.ovirt.engine.core.common.businessentities.StorageServerConnections;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmStatic;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.common.job.Job;
import org.ovirt.engine.core.common.job.JobExecutionStatus;
import org.ovirt.engine.core.common.locks.LockingGroup;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.common.vdscommands.FailedToRunVmVDSCommandParameters;
import org.ovirt.engine.core.common.vdscommands.UpdateVmDynamicDataVDSCommandParameters;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.dao.VdsDynamicDAO;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.threadpool.ThreadPoolUtil;
import org.ovirt.engine.core.vdsbroker.ResourceManager;
import org.ovirt.engine.core.vdsbroker.VdsMonitor;

/**
 * Base class for asynchronous running process handling
 */
public abstract class RunVmCommandBase<T extends VmOperationParameterBase> extends VmCommand<T> implements
        IVdsAsyncCommand, RunVmDelayer {

    private static Log log = LogFactory.getLog(RunVmCommandBase.class);
    protected static final HashMap<Guid, Integer> _vds_pending_vm_count = new HashMap<Guid, Integer>();
    protected boolean _isRerun = false;
    protected VDS _destinationVds;
    private SnapshotsValidator snapshotsValidator=new SnapshotsValidator();
    private final List<Guid> runVdsList = new ArrayList<Guid>();

    protected RunVmCommandBase(Guid commandId) {
        super(commandId);
    }

    public RunVmCommandBase(T parameters) {
        super(parameters);
    }

    protected abstract VDS getDestinationVds();

    public SnapshotsValidator getSnapshotsValidator() {
        return snapshotsValidator;
    }

    /**
     * List on all VDSs, vm run on. In the case of problem to run vm will be more then one
     */
    protected List<Guid> getRunVdssList() {
        return runVdsList;
    }

    @Override
    public void rerun() {
        Guid vdsId = getDestinationVds() != null ? getDestinationVds().getId() : getCurrentVdsId();
        decreasePendingVms(vdsId);

        setSucceeded(false);
        setVm(null);

        // by default, if rerun is called then rerun process is about to start so log the result of the
        //previous run as if rerun is about to begin (and change it later in case rerun isn't going to happen)
        _isRerun = true;
        log();

        /**
         * Rerun VM only if not exceeded maximum rerun attempts. for example if there are 10 hosts that can run VM and
         * predefine maximum 3 attempts to rerun VM - on 4th turn vm will stop to run despite there are still available
         * hosts to run it DO NOT TRY TO RERUN IF RESUME FAILED.
         */
        if (getRunVdssList().size() < Config.<Integer> GetValue(ConfigValues.MaxRerunVmOnVdsCount)
                && getVm().getStatus() != VMStatus.Paused) {
            // restore CanDoAction value to false so CanDoAction checks will run again
            getReturnValue().setCanDoAction(false);
            if (getExecutionContext() != null) {
                Job job = getExecutionContext().getJob();
                if (job != null) {
                    // mark previous steps as fail
                    JobRepositoryFactory.getJobRepository().closeCompletedJobSteps(job.getId(), JobExecutionStatus.FAILED);
                }
            }
            // set the _isRerun flag to false before calling executeAction so that we'll know if
            // there is another rerun attempt within the method
            _isRerun = false;
            insertAsyncTaskPlaceHolders();
            executeAction();

            // if there was no rerun attempt in the previous executeAction call and the command
            // wasn't done because canDoAction check returned false..
            if (!_isRerun && !getReturnValue().getCanDoAction()) {
                log();
                failedToRunVm();
            }

            // signal the caller that a rerun was made
            _isRerun = true;
        } else {
            Backend.getInstance().getResourceManager().RemoveAsyncRunningCommand(getVmId());
            failedToRunVm();
            _isRerun = false;
            log();
        }
    }

    protected void failedToRunVm() {
        ThreadPoolUtil.execute(new Runnable() {
            @Override
            public void run() {
                processVmPoolOnStopVm();
            }
        });
        ExecutionHandler.setAsyncJob(getExecutionContext(), false);
        ExecutionHandler.endJob(getExecutionContext(), false);
    }

    private void processVmPoolOnStopVm() {
        VmPoolHandler.processVmPoolOnStopVm(getVm().getId(),
                ExecutionHandler.createDefaultContexForTasks(getExecutionContext()));
    }

    /**
     * Asynchronous event, send by vds on running vm success. Vm decided successfully run when it's status turn to Up.
     * If there are vdss, not succeeded to run vm - treat them as suspicious.
     */
    @Override
    public void runningSucceded() {
        setSucceeded(true);
        setActionReturnValue(VMStatus.Up);
        log();
        ExecutionHandler.setAsyncJob(getExecutionContext(), false);
        ExecutionHandler.endJob(getExecutionContext(), true);
        notifyHostsVmFailed();

        if (getVm().getLastVdsRunOn() == null || !getVm().getLastVdsRunOn().equals(getCurrentVdsId())) {
            getVm().setLastVdsRunOn(getCurrentVdsId());
        }

        if (StringUtils.isNotEmpty(getVm().getHibernationVolHandle())) {
            removeVmHibernationVolumes();

            // In order to prevent a race where VdsUpdateRuntimeInfo saves the Vm Dynamic as UP prior to execution of
            // this method (which is a part of the cached VM command,
            // so the state this method is aware to is RESTORING, in case of RunVmCommand after the VM got suspended.
            // In addition, as the boolean return value of HandleHIbernateVm is ignored here, it is safe to set the
            // status to up.
            getVm().setStatus(VMStatus.Up);
            getVm().setHibernationVolHandle(null);
            Backend.getInstance()
                    .getResourceManager()
                    .RunVdsCommand(VDSCommandType.UpdateVmDynamicData,
                            new UpdateVmDynamicDataVDSCommandParameters(getCurrentVdsId(), getVm().getDynamicData()));
        }
    }

    private void removeVmHibernationVolumes() {
        RemoveVmHibernationVolumesParameters removeVmHibernationVolumesParameters = new RemoveVmHibernationVolumesParameters(getVmId());
        removeVmHibernationVolumesParameters.setParentCommand(getActionType());
        removeVmHibernationVolumesParameters.setEntityInfo(getParameters().getEntityInfo());
        removeVmHibernationVolumesParameters.setParentParameters(getParameters());

        VdcReturnValueBase vdcRetValue = getBackend().runInternalAction(
                VdcActionType.RemoveVmHibernationVolumes,
                removeVmHibernationVolumesParameters,
                ExecutionHandler.createDefaultContexForTasks(getExecutionContext()));

        for (Guid taskId : vdcRetValue.getInternalVdsmTaskIdList()) {
            AsyncTaskManager.getInstance().startPollingTask(taskId);
        }
    }

    /**
     * notify other hosts on a failed attempt to run a Vm in a non blocking matter
     * to avoid deadlock where other host's VdsManagers lock is taken and is awaiting the current vds lock.
     */
    private void notifyHostsVmFailed() {
        if (!getRunVdssList().isEmpty()) {
            ThreadPoolUtil.execute(new Runnable() {

                @Override
                public void run() {
                    for (Guid vdsId : getRunVdssList()) {
                        if (!getCurrentVdsId().equals(vdsId)) {
                            Backend.getInstance().getResourceManager()
                                    .RunVdsCommand(VDSCommandType.FailedToRunVm, new FailedToRunVmVDSCommandParameters(vdsId));
                        }
                    }
                }

            });
        }
    }

    @Override
    public void reportCompleted() {
        ExecutionContext executionContext = getExecutionContext();
        if (executionContext != null && executionContext.isMonitored()) {
            if (!executionContext.isCompleted()) {
                if (executionContext.getExecutionMethod() == ExecutionMethod.AsJob) {
                    ExecutionHandler.endJob(executionContext, false);
                } else if (executionContext.getExecutionMethod() == ExecutionMethod.AsStep) {
                    ExecutionHandler.endStep(executionContext, executionContext.getStep(), false);
                }
            }
        }
    }

    @Override
    protected void endVmCommand() {
        setCommandShouldBeLogged(false);
        setSucceeded(true);
    }

    protected Guid getCurrentVdsId() {
        return getVds().getId();
    }

    @Override
    public boolean getAutoStart() {
        return getVm().isAutoStartup();
    }

    @Override
    public Guid getAutoStartVdsId() {
        return null;
    }

    protected boolean connectLunDisks(Guid hostId) {
        if (getVm().getDiskMap().isEmpty()) {
            VmHandler.updateDisksFromDb(getVm());
        }
        List<LunDisk> lunDisks = ImagesHandler.filterDiskBasedOnLuns(getVm().getDiskMap().values());
        for (LunDisk lunDisk : lunDisks) {
            LUNs lun = lunDisk.getLun();
            lun.setLunConnections(new ArrayList<StorageServerConnections>(DbFacade.getInstance()
                                            .getStorageServerConnectionDao()
                                            .getAllForLun(lun.getLUN_id())));

            if (!lun.getLunConnections().isEmpty()
                    && !StorageHelperDirector.getInstance().getItem(lun.getLunConnections().get(0).getstorage_type())
                            .connectStorageToLunByVdsId(null, hostId, lun, getVm().getStoragePoolId())) {
                log.infoFormat("Failed to connect  a lun disk to vdsm {0} skiping it", hostId);
                return false;
            }

        }
        return true;
    }

    protected void decreasePendingVms(Guid vdsId) {
        VM vm = getVm();
        decreasePendingVms(vdsId, vm.getNumOfCpus(), vm.getMinAllocatedMem(), vm.getName());
    }

    protected void decreasePendingVms(Guid vdsId, int numOfCpus, int minAllocatedMem, String vmName) {
        getVdsDynamicDao().updatePartialVdsDynamicCalc(vdsId, 0, -numOfCpus, -minAllocatedMem, 0, 0);
        getBlockingQueue(vdsId).offer(Boolean.TRUE);

        if (log.isDebugEnabled()) {
            log.debugFormat("Decreasing vds {0} pending vcpu count by {1} and vmem size by {2} (Vm: {3})",
                    vdsId, numOfCpus, minAllocatedMem, vmName);
        }
    }

    protected VdsDynamicDAO getVdsDynamicDao() {
        return DbFacade.getInstance().getVdsDynamicDao();
    }

    /**
     * throttle bulk run of VMs by waiting for the update of run-time to kick in and fire <br>
     * the DecreasePendingVms event.
     * @see VdsEventListener
     * @See VdsUpdateRunTimeInfo
     */
    @Override
    public void delay(Guid vdsId) {
        log.debug("try to wait for te engine update the host memory and cpu stats");

        try {
            // time out waiting for an update is the highest between the refresh rate and the last update elapsed time
            // but still no higher than a configurable max to prevent very long updates to stall command.
            long t = Math.max(
                    ResourceManager.getInstance().GetVdsManager(vdsId).getLastUpdateElapsed(),
                    TimeUnit.SECONDS.toMillis(Config.<Integer> GetValue(VdsRefreshRate)));
            t = Math.max(Config.<Integer> GetValue(ConfigValues.ThrottlerMaxWaitForVdsUpdateInMillis), t);

            // wait for the run-time refresh to decrease any current powering-up VMs
            getBlockingQueue(vdsId).poll(t, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private BlockingQueue<Boolean> getBlockingQueue(Guid vdsId) {
        return getMonitor(vdsId).getQueue();
    }

    /**
     * get the monitor object of this host. VDSs have monitors exposed by their {@link org.ovirt.engine.core.vdsbroker.VdsManager}
     *
     * @param vdsId
     * @return {@link org.ovirt.engine.core.vdsbroker.VdsMonitor} for signaling on thread actions
     */
    private VdsMonitor getMonitor(Guid vdsId) {
        return ResourceManager.getInstance().GetVdsManager(vdsId).getVdsMonitor();
    }

    /**
     * Since this callback is called by the VdsUpdateRunTimeInfo thread, we don't want it
     * to fetch the VM using {@link #getVm()}, as the thread that invokes {@link #rerun()},
     * which runs in parallel, is doing setVm(null) to refresh the VM, and because of this
     * race we might end up with null VM. so we fetch the static part of the VM from the DB.
     */
    @Override
    public void onPowerringUp() {
        VmStatic vmStatic = getVmStaticDAO().get(getVmId());
        decreasePendingVms(getCurrentVdsId(), vmStatic.getNumOfCpus(),
                vmStatic.getMinAllocatedMem(), vmStatic.getName());
    }

    @Override
    protected Map<String, Pair<String, String>> getExclusiveLocks() {
        return getExclusiveLocksForRunVm(getVmId(), getLockMessage());
    }

    @Override
    protected Map<String, Pair<String, String>> getSharedLocks() {
        return getSharedLocksForRunVm();
    }

    protected static Map<String, Pair<String, String>> getExclusiveLocksForRunVm(Guid vmId, String lockMessage) {
        return Collections.singletonMap(
                vmId.toString(),
                LockMessagesMatchUtil.makeLockingPair(
                        LockingGroup.VM,
                        lockMessage));
    }

    protected static Map<String, Pair<String, String>> getSharedLocksForRunVm() {
        return null;
    }

    /**
     * Returns a message that explains what this command does. The message is
     * shown when other command is blocked because it conflicts with this
     * command, so the user should understand what cause it the conflict.
     *
     * @return String explaining what this command does
     */
    protected String getLockMessage() {
        return VdcBllMessages.ACTION_TYPE_FAILED_OBJECT_LOCKED.name();
    }
}
