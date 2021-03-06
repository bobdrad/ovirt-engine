package org.ovirt.engine.core.bll;

import java.util.List;

import org.ovirt.engine.core.bll.context.CompensationContext;
import org.ovirt.engine.core.common.backendinterfaces.BaseHandler;
import org.ovirt.engine.core.common.businessentities.Disk;
import org.ovirt.engine.core.common.businessentities.DiskImage;
import org.ovirt.engine.core.common.businessentities.EditableField;
import org.ovirt.engine.core.common.businessentities.EditableOnTemplate;
import org.ovirt.engine.core.common.businessentities.VmBase;
import org.ovirt.engine.core.common.businessentities.VmTemplate;
import org.ovirt.engine.core.common.businessentities.VmTemplateStatus;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.utils.ObjectIdentityChecker;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.transaction.TransactionMethod;
import org.ovirt.engine.core.utils.transaction.TransactionSupport;

public class VmTemplateHandler {
    protected static Guid BlankVmTemplateId = Guid.Empty;
    protected final static String BlankVmTemplateName = "Blank";
    private static ObjectIdentityChecker mUpdateVmTemplate;

    /**
     * Initialize static list containers, for identity and permission check. The initialization should be executed
     * before calling ObjectIdentityChecker.
     *
     * @see Backend#InitHandlers
     */
    public static void Init() {
        final Class<?>[] inspectedClassNames = new Class<?>[] { VmBase.class, VmTemplate.class };
        mUpdateVmTemplate = new ObjectIdentityChecker(VmTemplateHandler.class);
        BlankVmTemplateId = new Guid("00000000-0000-0000-0000-000000000000");

        for (Pair<EditableField, String> pair : BaseHandler.extractAnnotatedFields(EditableField.class,
                                                                                   (inspectedClassNames))) {
            mUpdateVmTemplate.AddPermittedFields(pair.getSecond());
        }

        for (Pair<EditableOnTemplate, String> pair : BaseHandler.extractAnnotatedFields(EditableOnTemplate.class, inspectedClassNames)) {
            mUpdateVmTemplate.AddPermittedFields(pair.getSecond());
        }
    }

    public static boolean isUpdateValid(VmTemplate source, VmTemplate destination) {
        return mUpdateVmTemplate.IsUpdateValid(source, destination);
    }

    public static void UpdateDisksFromDb(VmTemplate vmt) {
        vmt.getDiskTemplateMap().clear();
        vmt.getDiskImageMap().clear();
        vmt.getDiskList().clear();
        List<Disk> diskList = DbFacade.getInstance().getDiskDao().getAllForVm(vmt.getId());
        for (Disk dit : diskList) {
            DiskImage diskImage = (DiskImage) dit;
            vmt.getDiskTemplateMap().put(dit.getId(), diskImage);
            // Translation from number of sectors to GB.
            vmt.setSizeGB(Double.valueOf(dit.getSize()) / Double.valueOf((1024 * 1024 * 1024)));
            vmt.getDiskImageMap().put(dit.getId(), diskImage);
            vmt.getDiskList().add(diskImage);
        }
    }

    /**
     * Lock the VM template with the given id in a new transaction, handling the compensation data using the given
     * {@link CompensationContext}.
     *
     * @param vmTemplateGuid
     *            The id of the template to lock.
     * @param compensationContext
     *            The compensation context for saving the old status (can't be <code>null</code>).
     */
    public static void lockVmTemplateInTransaction(final Guid vmTemplateGuid,
            final CompensationContext compensationContext) {
        TransactionSupport.executeInNewTransaction(new TransactionMethod<Void>() {
            @Override
            public Void runInTransaction() {
                SetVmTemplateStatus(vmTemplateGuid, VmTemplateStatus.Locked, compensationContext);
                compensationContext.stateChanged();
                return null;
            }
        });
    }

    public static void UnLockVmTemplate(Guid vmTemplateGuid) {
        SetVmTemplateStatus(vmTemplateGuid, VmTemplateStatus.OK, null);
    }

    public static void MarkVmTemplateAsIllegal(Guid vmTemplateGuid) {
        SetVmTemplateStatus(vmTemplateGuid, VmTemplateStatus.Illegal, null);
    }

    /**
     * Set the status of the VM template with the given id to the desired status, saving the old status if necessary.
     *
     * @param vmTemplateGuid
     *            The id of the template to set the status for.
     * @param status
     *            The status to set.
     * @param compensationContext
     *            The compensation context for saving the old status (can be <code>null</code> if the old status is not
     *            required to be saved).
     */
    private static void SetVmTemplateStatus(
            Guid vmTemplateGuid, VmTemplateStatus status, CompensationContext compensationContext) {
        VmTemplate vmTemplate = DbFacade.getInstance().getVmTemplateDao().get(vmTemplateGuid);
        if (vmTemplate != null) {
            if (compensationContext != null) {
                compensationContext.snapshotEntityStatus(vmTemplate);
            }
            vmTemplate.setStatus(status);
            DbFacade.getInstance().getVmTemplateDao().update(vmTemplate);
        } else {
            log.warnFormat(
                    "VmTemplateHandler::SetVmTemplateStatus: vmTemplate is null, not setting status '{0}' to vmTemplate",
                    status);
        }
    }

    private static Log log = LogFactory.getLog(VmTemplateHandler.class);
}
