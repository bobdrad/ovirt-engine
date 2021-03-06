package org.ovirt.engine.ui.uicommonweb.models.vms;

import java.util.ArrayList;

import org.ovirt.engine.core.common.action.AddDiskParameters;
import org.ovirt.engine.core.common.action.AttachDettachVmDiskParameters;
import org.ovirt.engine.core.common.action.VdcActionParametersBase;
import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.businessentities.DiskImage;
import org.ovirt.engine.core.common.businessentities.DiskInterface;
import org.ovirt.engine.core.common.businessentities.LUNs;
import org.ovirt.engine.core.common.businessentities.LunDisk;
import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.common.businessentities.StoragePool;
import org.ovirt.engine.core.common.businessentities.StoragePoolStatus;
import org.ovirt.engine.core.common.businessentities.StorageType;
import org.ovirt.engine.core.common.businessentities.VolumeType;
import org.ovirt.engine.core.common.queries.ConfigurationValues;
import org.ovirt.engine.ui.frontend.AsyncQuery;
import org.ovirt.engine.ui.frontend.Frontend;
import org.ovirt.engine.ui.frontend.INewAsyncCallback;
import org.ovirt.engine.ui.uicommonweb.dataprovider.AsyncDataProvider;
import org.ovirt.engine.ui.uicommonweb.models.EntityModel;
import org.ovirt.engine.ui.uicommonweb.models.ListModel;
import org.ovirt.engine.ui.uicommonweb.models.SystemTreeItemModel;
import org.ovirt.engine.ui.uicommonweb.models.SystemTreeItemType;
import org.ovirt.engine.ui.uicommonweb.validation.IValidation;
import org.ovirt.engine.ui.uicommonweb.validation.IntegerValidation;
import org.ovirt.engine.ui.uicommonweb.validation.NotEmptyValidation;
import org.ovirt.engine.ui.uicompat.FrontendActionAsyncResult;
import org.ovirt.engine.ui.uicompat.IFrontendActionAsyncCallback;

public class NewDiskModel extends AbstractDiskModel
{
    public NewDiskModel() {
    }

    public NewDiskModel(SystemTreeItemModel systemTreeSelectedItem) {
        setSystemTreeSelectedItem(systemTreeSelectedItem);
    }

    @Override
    public void initialize() {
        super.initialize();

        if (getVm() != null) {
            updateSuggestedDiskAlias();
        } else {
            // Read only disk can be created only in the scope of VM.
            getIsReadOnly().setIsAvailable(false);
        }

        getSizeExtend().setIsAvailable(false);
    }

    private void updateSuggestedDiskAlias() {
        AsyncDataProvider.getNextAvailableDiskAliasNameByVMId(new AsyncQuery(this, new INewAsyncCallback() {
            @Override
            public void onSuccess(Object model, Object returnValue) {
                String suggestedDiskAlias = (String) returnValue;
                DiskModel diskModel = (DiskModel) model;
                diskModel.getAlias().setEntity(suggestedDiskAlias);
            }
        }, getHash()), getVm().getId());
    }

    private void onAttachDisks()
    {
        ArrayList<VdcActionType> actionTypes = new ArrayList<VdcActionType>();
        ArrayList<VdcActionParametersBase> paramerterList = new ArrayList<VdcActionParametersBase>();
        ArrayList<IFrontendActionAsyncCallback> callbacks = new ArrayList<IFrontendActionAsyncCallback>();

        IFrontendActionAsyncCallback onFinishCallback = new IFrontendActionAsyncCallback() {
            @Override
            public void executed(FrontendActionAsyncResult result) {
                NewDiskModel diskModel = (NewDiskModel) result.getState();
                diskModel.stopProgress();
                diskModel.cancel();
                postSave();
            }
        };

        ArrayList<EntityModel> disksToAttach = (Boolean) getIsInternal().getEntity() ?
                (ArrayList<EntityModel>) getInternalAttachableDisks().getSelectedItems() :
                (ArrayList<EntityModel>) getExternalAttachableDisks().getSelectedItems();

        for (int i = 0; i < disksToAttach.size(); i++) {
            DiskModel disk = (DiskModel) disksToAttach.get(i).getEntity();
            // Disk is attached to VM as read only or not, null is applicable only for floating disks
            // but this is not a case here.
            AttachDettachVmDiskParameters parameters = new AttachDettachVmDiskParameters(
                    getVm().getId(), disk.getDisk().getId(), (Boolean) getIsPlugged().getEntity(),
                    Boolean.TRUE.equals(disk.getDisk().getReadOnly()));

            actionTypes.add(VdcActionType.AttachDiskToVm);
            paramerterList.add(parameters);
            callbacks.add(i == disksToAttach.size() - 1 ? onFinishCallback : null);
        }

        startProgress(null);

        Frontend.RunMultipleActions(actionTypes, paramerterList, callbacks, null, this);
    }

    @Override
    public boolean getIsNew() {
        return true;
    }

    @Override
    protected boolean isDatacenterAvailable(StoragePool dataCenter) {
        boolean isStatusUp = dataCenter.getStatus() == StoragePoolStatus.Up;

        boolean isInTreeContext = true;
        if (getSystemTreeSelectedItem() != null && getSystemTreeSelectedItem().getType() != SystemTreeItemType.System)
        {
            switch (getSystemTreeSelectedItem().getType())
            {
            case DataCenter:
                StoragePool selectedDataCenter = (StoragePool) getSystemTreeSelectedItem().getEntity();
                isInTreeContext = selectedDataCenter.getId().equals(dataCenter.getId());
            default:
                break;
            }
        }

        return isStatusUp && isInTreeContext;
    }

    @Override
    protected void updateWipeAfterDelete(StorageType storageType) {
        if (storageType.isFileDomain()) {
            getIsWipeAfterDelete().setChangeProhibitionReason(CONSTANTS.wipeAfterDeleteNotSupportedForFileDomains());
            getIsWipeAfterDelete().setIsChangable(false);
            getIsWipeAfterDelete().setEntity(false);
        }
        else {
            getIsWipeAfterDelete().setIsChangable(true);
            getIsWipeAfterDelete().setEntity(AsyncDataProvider.getConfigValuePreConverted(ConfigurationValues.SANWipeAfterDelete));
        }
    }

    @Override
    public void setDefaultInterface() {
        getDiskInterface().setSelectedItem(DiskInterface.VirtIO);
    }

    @Override
    protected DiskImage getDiskImage() {
        return new DiskImage();
    }

    @Override
    protected LunDisk getLunDisk() {
        return new LunDisk();
    }

    @Override
    public void onSave() {
        if (getProgress() != null || !validate()) {
            return;
        }

        if ((Boolean) getIsAttachDisk().getEntity()) {
            onAttachDisks();
            return;
        }

        super.onSave();

        boolean isInternal = (Boolean) getIsInternal().getEntity();
        if (isInternal) {
            DiskImage diskImage = (DiskImage) getDisk();
            diskImage.setSizeInGigabytes(Integer.parseInt(getSize().getEntity().toString()));
            diskImage.setVolumeType((VolumeType) getVolumeType().getSelectedItem());
            diskImage.setvolumeFormat(getVolumeFormat());
        }
        else {
            LunDisk lunDisk = (LunDisk) getDisk();
            LUNs luns = (LUNs) getSanStorageModel().getAddedLuns().get(0).getEntity();
            luns.setLunType((StorageType) getStorageType().getSelectedItem());
            lunDisk.setLun(luns);
        }

        startProgress(null);

        AddDiskParameters parameters = new AddDiskParameters(getVmId(), getDisk());
        if ((Boolean) getIsInternal().getEntity()) {
            StorageDomain storageDomain = (StorageDomain) getStorageDomain().getSelectedItem();
            parameters.setStorageDomainId(storageDomain.getId());
        }

        Frontend.RunAction(VdcActionType.AddDisk, parameters, new IFrontendActionAsyncCallback() {
            @Override
            public void executed(FrontendActionAsyncResult result) {
                NewDiskModel diskModel = (NewDiskModel) result.getState();
                diskModel.stopProgress();
                diskModel.cancel();
                postSave();
            }
        }, this);
    }

    protected void postSave() {
        // empty by default
    }

    @Override
    public boolean validate() {
        super.validate();

        if ((Boolean) getIsAttachDisk().getEntity()) {
            if (isSelectionsEmpty(getInternalAttachableDisks()) && isSelectionsEmpty(getExternalAttachableDisks())) {
                getInvalidityReasons().add(CONSTANTS.noDisksSelected());
                setIsValid(false);
                return false;
            }

            return true;
        }

        if (!(Boolean) getIsInternal().getEntity() && getSanStorageModel() != null) {
            getSanStorageModel().validate();
            if (!getSanStorageModel().getIsValid()) {
                return false;
            }

            ArrayList<String> partOfSdLunsMessages = getSanStorageModel().getPartOfSdLunsMessages();
            if (!partOfSdLunsMessages.isEmpty() && !getSanStorageModel().isForce()) {
                forceCreationWarning(partOfSdLunsMessages);
                return false;
            }
        }

        StorageType storageType = getStorageDomain().getSelectedItem() == null ? StorageType.UNKNOWN
                : ((StorageDomain) getStorageDomain().getSelectedItem()).getStorageType();
        IntegerValidation sizeValidation = new IntegerValidation();
        sizeValidation.setMinimum(1);
        if (storageType.isBlockDomain()) {
            sizeValidation.setMaximum((Integer) AsyncDataProvider.getConfigValuePreConverted(ConfigurationValues.MaxBlockDiskSize));
        }
        getSize().validateEntity(new IValidation[] { new NotEmptyValidation(), sizeValidation });
        getStorageDomain().validateSelectedItem(new IValidation[] { new NotEmptyValidation() });

        return super.validate() && getSize().getIsValid() && getStorageDomain().getIsValid();
    }

    private boolean isSelectionsEmpty(ListModel listModel) {
        return listModel.getSelectedItems() == null || listModel.getSelectedItems().isEmpty();
    }
}
