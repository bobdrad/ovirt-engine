package org.ovirt.engine.core.bll.gluster;

import org.ovirt.engine.core.bll.LockIdNameAttribute;
import org.ovirt.engine.core.bll.NonTransactiveCommandAttribute;
import org.ovirt.engine.core.bll.gluster.tasks.GlusterTaskUtils;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.action.gluster.GlusterVolumeActionParameters;
import org.ovirt.engine.core.common.asynctasks.gluster.GlusterTaskType;
import org.ovirt.engine.core.common.businessentities.gluster.GlusterStatus;
import org.ovirt.engine.core.common.businessentities.gluster.GlusterVolumeEntity;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.common.job.JobExecutionStatus;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.common.vdscommands.VDSReturnValue;
import org.ovirt.engine.core.common.vdscommands.gluster.GlusterVolumeActionVDSParameters;
import org.ovirt.engine.core.dao.gluster.GlusterDBUtils;

/**
 * BLL command to stop a Gluster volume
 */
@NonTransactiveCommandAttribute
@LockIdNameAttribute(isWait = true)
public class StopGlusterVolumeCommand extends GlusterVolumeCommandBase<GlusterVolumeActionParameters> {

    public StopGlusterVolumeCommand(GlusterVolumeActionParameters params) {
        super(params);
    }

    @Override
    protected void setActionMessageParameters() {
        addCanDoActionMessage(VdcBllMessages.VAR__ACTION__STOP);
        addCanDoActionMessage(VdcBllMessages.VAR__TYPE__GLUSTER_VOLUME);
        addCanDoActionMessage(String.format("$volumeName %1$s", getGlusterVolumeName()));
        addCanDoActionMessage(String.format("$vdsGroup %1$s", getVdsGroupName()));
    }

    @Override
    protected boolean canDoAction() {
        if(! super.canDoAction()) {
            return false;
        }

        GlusterVolumeEntity volume = getGlusterVolume();
        if (!volume.isOnline()) {
            addCanDoActionMessage(VdcBllMessages.ACTION_TYPE_FAILED_GLUSTER_VOLUME_ALREADY_STOPPED);
            addCanDoActionMessage(String.format("$volumeName %1$s", volume.getName()));
            return false;
        }

        if (getGlusterTaskUtils().isTaskOfType(volume, GlusterTaskType.REBALANCE)
                && getGlusterTaskUtils().isTaskStatus(volume, JobExecutionStatus.STARTED)) {
            return failCanDoAction(VdcBllMessages.ACTION_TYPE_FAILED_GLUSTER_VOLUME_CANNOT_STOP_REBALANCE_IN_PROGRESS);
        }

        return true;
    }

    @Override
    protected void executeCommand() {
        VDSReturnValue returnValue =
                                runVdsCommand(
                                        VDSCommandType.StopGlusterVolume,
                                        new GlusterVolumeActionVDSParameters(upServer.getId(),
                                                getGlusterVolumeName(), getParameters().isForceAction()));
        setSucceeded(returnValue.getSucceeded());
        if (getSucceeded()) {
            GlusterDBUtils.getInstance().updateVolumeStatus(getParameters().getVolumeId(), GlusterStatus.DOWN);
        } else {
            handleVdsError(AuditLogType.GLUSTER_VOLUME_STOP_FAILED, returnValue.getVdsError().getMessage());
            return;
        }
    }

    @Override
    public AuditLogType getAuditLogTypeValue() {
        if (getSucceeded()) {
            return AuditLogType.GLUSTER_VOLUME_STOP;
        } else {
            return errorType == null ? AuditLogType.GLUSTER_VOLUME_STOP_FAILED : errorType;
        }
    }

    public GlusterTaskUtils getGlusterTaskUtils() {
        return GlusterTaskUtils.getInstance();
    }
}
