package org.ovirt.engine.core.common.action;

import org.ovirt.engine.core.compat.Guid;

public class VmOperationParameterBase extends VdcActionParametersBase implements java.io.Serializable {
    private static final long serialVersionUID = -6248335374537898949L;
    private Guid quotaId;

    public VmOperationParameterBase() {
        vmId = Guid.Empty;
    }

    private Guid vmId;

    public VmOperationParameterBase(Guid vmId) {
        this.vmId = vmId;
    }

    public Guid getVmId() {
        return vmId;
    }

    public void setVmId(Guid value) {
        vmId = value;
    }

    public Guid getQuotaId() {
        return quotaId;
    }

    public void setQuotaId(Guid value) {
        quotaId = value;
    }
}
