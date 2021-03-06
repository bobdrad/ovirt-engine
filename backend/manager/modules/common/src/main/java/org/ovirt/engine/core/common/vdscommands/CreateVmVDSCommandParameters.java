package org.ovirt.engine.core.common.vdscommands;

import org.ovirt.engine.core.common.action.CloudInitParameters;
import org.ovirt.engine.core.common.action.SysPrepParams;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.compat.Guid;

public class CreateVmVDSCommandParameters extends VdsAndVmIDVDSParametersBase {

    private VM vm;
    private CloudInitParameters cloudInitParameters;
    private SysPrepParams sysPrepParams;
    private boolean clearHibernationVolumes;

    public CreateVmVDSCommandParameters() {
    }

    public CreateVmVDSCommandParameters(Guid vdsId, VM vm) {
        super(vdsId, vm.getId());
        this.vm = vm;
    }

    public VM getVm() {
        return vm;
    }

    @Override
    public String toString() {
        return String.format("%s, vm=%s", super.toString(), getVm());
    }

    public CloudInitParameters getCloudInitParameters() {
        return cloudInitParameters;
    }

    public void setCloudInitParameters(CloudInitParameters CloudInitParameters) {
        this.cloudInitParameters = CloudInitParameters;
    }

    public SysPrepParams getSysPrepParams() {
        return sysPrepParams;
    }

    public void setSysPrepParams(SysPrepParams sysPrepParams) {
        this.sysPrepParams = sysPrepParams;
    }

    public boolean isClearHibernationVolumes() {
        return clearHibernationVolumes;
    }

    public void setClearHibernationVolumes(boolean clearHibernationVolumes) {
        this.clearHibernationVolumes = clearHibernationVolumes;
    }
}
