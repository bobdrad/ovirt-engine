package org.ovirt.engine.core.common.errors;

import java.util.HashMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

/**
 * The purpose of this enumaration is to contain all the errors exposed by the VdcBLL. The error codes are not
 * sequential in order to be able to add error codes as development evolves.
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "VdcBllErrors")
public enum VdcBllErrors {
    Done(0),
    noVM(1),
    nfsErr(3),
    exist(4),
    noVmType(5),
    down(6),
    copyerr(7),
    sparse(8),
    createErr(9),
    noConPeer(10),
    MissParam(11),
    migrateErr(12),
    imageErr(13),
    outOfMem(14),
    unexpected(16),
    unsupFormat(17),
    ticketErr(18),
    nonresp(19),
    ERR_BAD_PARAMS(21),
    ERR_BAD_ADDR(22),
    ERR_BAD_NIC(23),
    ERR_USED_NIC(24),
    ERR_BAD_BONDING(25),
    ERR_BAD_VLAN(26),
    ERR_BAD_BRIDGE(27),
    ERR_USED_BRIDGE(28),
    MIGRATION_DEST_INVALID_HOSTNAME(39),
    unavail(40),
    FAILED_CHANGE_CD_IS_MOUNTED(41),
    destroyErr(42),
    fenceAgent(43),
    NO_IMPLEMENTATION(44),
    FailedToPlugDisk(45),
    FailedToUnPlugDisk(46),
    MIGRATION_CANCEL_ERROR(47),
    SNAPSHOT_FAILED(48),
    recovery(99),
    GeneralException(100),
    StorageException(200),
    VolumeDoesNotExist(201),
    IncorrectFormat(202),
    VolumeIsBusy(203),
    VolumeImageHasChildren(204),
    VolumeCreationError(205),
    VolumeExtendingError(206),
    VolumeMetadataReadError(207),
    VolumeMetadataWriteError(208),
    VolumeAccessError(209),
    VolumeUnlinkError(210),
    OrphanVolumeError(211),
    VolumeAlreadyExists(212),
    VolumeNonWritable(213),
    VolumeNonShareable(214),
    VolumeOwnershipError(215),
    VolumeCannotGetParent(216),
    CannotCloneVolume(217),
    CannotShareVolume(218),
    SharedVolumeNonWritable(219),
    InternalVolumeNonWritable(220),
    CannotModifyVolumeTime(221),
    CannotDeleteVolume(222),
    CannotDeleteSharedVolume(223),
    NonLeafVolumeNotWritable(224),
    VolumeCopyError(225),
    createIllegalVolumeSnapshotError(226),
    prepareIllegalVolumeError(227),
    createVolumeRollbackError(228),
    createVolumeSizeError(229),
    VOLUME_WAS_NOT_PREPARED_BEFORE_TEARDOWN(230),
    ImagesActionError(250),
    TemplateCreationError(251),
    MergeSnapshotsError(252),
    MoveImageError(253),
    ImagePathError(254),
    ImageValidationError(255),
    ImageDeleteError(256),
    ImageIsNotEmpty(257),
    ImageIsEmpty(258),
    SourceImageActionError(259),
    DestImageActionError(260),
    CopyImageError(261),
    ImageIsNotLegalChain(262),
    CouldNotValideTemplateOnTargetDomain(263),
    MultipleMoveImageError(264),
    OverwriteImageError(265),
    MoveTemplateImageError(266),
    MergeVolumeRollbackError(267),
    ImageDoesNotExistInDomainError(268),
    StoragePoolActionError(300),
    StoragePoolCreationError(301),
    StoragePoolConnectionError(302),
    StoragePoolDisconnectionError(303),
    StoragePoolMasterNotFound(304),
    StorageUpdateVmError(305),
    ReconstructMasterError(306),
    StoragePoolTooManyMasters(307),
    StoragePoolDestroyingError(308),
    StoragePoolUnknown(309),
    StoragePoolHasPotentialMaster(310),
    StoragePoolInternalError(311),
    ImageMissingFromVm(312),
    StoragePoolNotConnected(313),
    GetIsoListError(314),
    GetFloppyListError(315),
    StoragePoolAlreadyExists(316),
    IsoCannotBeMasterDomain(317),
    StoragePoolCheckError(318),
    BackupCannotBeMasterDomain(319),
    MissingOvfFileFromVM(320),
    ImageNotOnTargetDomain(321),
    VMPathNotExists(322),
    CannotConnectMultiplePools(323),
    StoragePoolWrongMaster(324),
    StoragePoolConnected(325),
    StoragePoolHigherVersionMasterFound(326),
    StoragePoolDescriptionTooLongError(327),
    TooManyDomainsInStoragePoolError(328),
    IMAGES_NOT_SUPPORTED_ERROR(329),
    GET_FILE_LIST_ERROR(330),
    StorageDomainActionError(350),
    StorageDomainCreationError(351),
    StorageDomainFormatError(352),
    StorageDomainNotInPool(353),
    StorageDomainAttachError(354),
    StorageDomainMasterError(355),
    StorageDomainDetachError(356),
    StorageDomainDeactivateError(357),
    StorageDomainDoesNotExist(358),
    StorageDomainActivateError(359),
    StorageDomainFSNotMounted(360),
    StorageDomainNotEmpty(361),
    StorageDomainMetadataCreationError(362),
    StorageDomainMetadataFileMissing(363),
    StorageDomainMetadataNotFound(364),
    StorageDomainAlreadyExists(365),
    StorageDomainMasterUnmountError(366),
    BlockStorageDomainMasterFSCKError(367),
    BlockStorageDomainMasterMountError(368),
    StorageDomainNotActive(369),
    StorageDomainMasterCopyError(370),
    StorageDomainLayoutError(371),
    StorageDomainTypeError(372),
    GetStorageDomainListError(373),
    VolumesZeroingError(374),
    StorageDomainNotMemberOfPool(375),
    StorageDomainStatusError(376),
    StorageDomainCheckError(377),
    StorageDomainTypeNotBackup(378),
    StorageDomainAccessError(379),
    StorageDomainAlreadyAttached(380),
    StorageDomainStateTransitionIllegal(381),
    StorageDomainActive(382),
    CannotDetachMasterStorageDomain(383),
    FileStorageDomainStaleNFSHandle(384),
    StorageDomainInsufficientPermissions(385),
    StorageDomainClassError(386),
    StorageDomainDescriptionTooLongError(387),
    StorageDomainIsMadeFromTooManyPVs(388),
    TooManyPVsInVG(389),
    StorageDomainIllegalRemotePath(390),
    CannotFormatAttachedStorageDomain(391),
    CannotFormatStorageDomainInConnectedPool(392),
    STORAGE_DOMAIN_REFRESH_ERROR(393),
    UnsupportedDomainVersion(394),
    CurrentVersionTooAdvancedError(395),
    PoolUpgradeInProgress(396),
    NoSpaceLeftOnDomain(397),
    MixedSDVersionError(398),
    InvalidTask(400),
    UnknownTask(401),
    TaskClearError(402),
    TaskNotFinished(403),
    InvalidTaskType(404),
    AddTaskError(405),
    TaskInProgress(406),
    TaskMetaDataSaveError(407),
    TaskMetaDataLoadError(408),
    TaskDirError(409),
    TaskStateError(410),
    TaskAborted(411),
    UnmanagedTask(412),
    TaskPersistError(413),
    InvalidJob(420),
    InvalidRecovery(430),
    InvalidTaskMng(440),
    TaskStateTransitionError(441),
    TaskHasRefs(442),
    // task was deliberately stopped by someone
    ActionStopped(443),
    StorageServerActionError(450),
    StorageServerConnectionError(451),
    StorageServerDisconnectionError(452),
    StorageServerValidationError(453),
    StorageServeriSCSIError(454),
    MultipathRestartError(455),
    GetiSCSISessionListError(456),
    AddiSCSIPortalError(457),
    RemoveiSCSIPortalError(458),
    RemoveiSCSINodeError(459),
    AddiSCSINodeError(460),
    SetiSCSIAuthError(461),
    SetiSCSIUsernameError(462),
    SetiSCSIPasswdError(463),
    iSCSILoginError(464),
    iSCSISetupError(465),
    DeviceNotFound(466),
    MultipathSetupError(467),
    StorageTypeError(468),
    StorageServerAccessPermissionError(469),
    MountTypeError(470),
    MountParsingError(471),
    InvalidIpAddress(472),
    iSCSIifaceError(473),
    iSCSILogoutError(474),
    iSCSIDiscoveryError(475),
    ISCSI_LOGIN_AUTH_ERROR(476),
    VolumeGroupActionError(500),
    VolumeGroupPermissionsError(501),
    VolumeGroupCreateError(502),
    VolumeGroupExtendError(503),
    VolumeGroupSizeError(504),
    VolumeGroupAlreadyExistsError(505),
    VolumeGroupDoesNotExist(506),
    VolumeGroupRenameError(507),
    VolumeGroupRemoveError(508),
    VolumeGroupUninitialized(509),
    VolumeGroupReadTagError(510),
    VolumeGroupAddTagError(511),
    VolumeGroupRemoveTagError(512),
    VolumeGroupScanError(513),
    GetVolumeGroupListError(514),
    VolumeGroupHasDomainTag(515),
    VolumeGroupReplaceTagError(516),
    VOLUME_GROUP_BLOCK_SIZE_ERROR(517),
    DEVICE_BLOCK_SIZE_NOT_SUPPORTED(518),
    CannotCreateLogicalVolume(550),
    CannotRemoveLogicalVolume(551),
    CannotDeactivateLogicalVolume(552),
    CannotAccessLogicalVolume(553),
    LogicalVolumeExtendError(554),
    LogicalVolumesListError(555),
    LogicalVolumeRefreshError(556),
    LogicalVolumeScanError(557),
    CannotActivateLogicalVolume(558),
    LogicalVolumePermissionsError(559),
    LogicalVolumeAddTagError(560),
    LogicalVolumeRemoveTagError(561),
    GetLogicalVolumeTagError(562),
    GetLogicalVolumesByTagError(563),
    GetAllLogicalVolumeTagsError(564),
    GetLogicalVolumeDevError(565),
    LogicalVolumeRenameError(566),
    CannotWriteAccessLogialVolume(567),
    CannotSetRWLogicalVolume(568),
    LogicalVolumesScanError(569),
    CannotActivateLogicalVolumes(570),
    GetLogicalVolumeDataError(571),
    LogicalVolumeReplaceTagError(572),
    BlockDeviceActionError(600),
    PhysDevInitializationError(601),
    LVMSetupError(602),
    CouldNotRetrievePhysicalVolumeList(603),
    LogicalVolumeAlreadyExists(604),
    CouldNotRetrieveLogicalVolumesList(605),
    InvalidPhysDev(606),
    PartitionedPhysDev(607),
    MkfsError(608),
    MissingTagOnLogicalVolume(609),
    LogicalVolumeDoesNotExistError(610),
    LogicalVolumeCachingError(611),
    LogicalVolumeWrongTagError(612),
    VG_METADATA_CRITICALLY_FULL(613),
    SMALL_VG_METADATA(614),
    SpmStartError(650),
    AcquireLockFailure(651),
    SpmParamsMismatch(652),
    SpmStopError(653),
    SpmStatusError(654),
    SpmFenceError(655),
    IsSpm(656),
    DomainAlreadyLocked(657),
    DomainLockDoesNotExist(658),
    CannotRetrieveSpmStatus(659),
    HostIdMismatch(700),
    MetaDataGeneralError(749),
    MetaDataKeyError(750),
    MetaDataKeyNotFoundError(751),
    MetaDataSealIsBroken(752),
    MetaDataValidationError(753),
    MetaDataMappingError(754),
    MetaDataParamError(755),
    MetadataOverflowError(756),
    ImportError(800),
    ImportInfoError(801),
    ImportUnknownType(802),
    ExportError(803),
    ResourceNamespaceNotEmpty(850),
    ResourceTimeout(851),
    ResourceDoesNotExist(852),
    InvalidResourceName(853),
    ResourceReferenceInvalid(854),
    ResourceAcqusitionFailed(855),
    InvalidParameterException(1000),
    InvalidDefaultExceptionException(1001),
    NotImplementedException(2000),
    MiscFileReadException(2001),
    MiscFileWriteException(2002),
    MiscBlockReadException(2003),
    MiscBlockWriteException(2004),
    OperationInProgress(2005),
    MiscBlockWriteIncomplete(2006),
    MiscBlockReadIncomplete(2007),
    MiscDirCleanupFailure(2008),
    ResourceException(3000),
    VolumeGeneralException(4000),

    // Gluster VDSM errors
    GlusterVolumeCreateFailed(4122),
    GlusterVolumeSetOptionFailed(4131),

    UnicodeArgumentException(4900),

    // oVirt errors
    ENGINE(5001),
    DB(5002),
    // The VDS does not exist in memory
    RESOURCE_MANAGER_VDS_NOT_FOUND(5004),
    IRS_IMAGE_STATUS_ILLEGAL(5006),
    // when trying to run vm from snapshot bt the snapshot does not belong to the VM's history
    RESOURCE_MANAGER_VM_SNAPSHOT_MISSMATCH(5007),
    VDS_SHUTDOWN_ERROR(5008),
    IRS_REPOSITORY_NOT_FOUND(5009),
    MAC_POOL_INITIALIZATION_FAILED(5010),
    MAC_POOL_NOT_INITIALIZED(5011),
    MAC_POOL_NO_MACS_LEFT(5012),
    VM_POOL_CANNOT_ALLOCATE_VM(5014),
    // Could not allocate VDS for a new VM to run on
    RESOURCE_MANAGER_CANT_ALLOC_VDS_MIGRATION(5015),
    RESOURCE_MANAGER_MIGRATION_FAILED_AT_DST(5016),
    VM_INVALID_SERVER_CLUSTER_ID(5017),
    VM_TEMPLATE_CANT_LOCATE_DISKS_IN_DB(5018),
    USER_FAILED_POPULATE_DATA(5019),
    DB_NO_SUCH_VM(5020),
    VDS_FENCING_OPERATION_FAILED(5021),
    VDS_NETWORK_ERROR(5022),
    NO_FREE_VM_IN_POOL(5023),
    ENGINE_ERROR_CREATING_STORAGE_POOL(5024),
    CANT_RECONSTRUCT_WHEN_A_DOMAIN_IN_POOL_IS_LOCKED(5025),
    NO_PARAMETERS_FOR_TASK(5026),
    HOST_ALREADY_EXISTS(5027),
    // Gluster errors
    NO_UP_SERVER_FOUND(7000),
    // error to indicate backend does not recognize the session
    SESSION_ERROR(9999), ;

    private int intValue;
    private static java.util.HashMap<Integer, VdcBllErrors> mappings = new HashMap<Integer, VdcBllErrors>();

    static {
        for (VdcBllErrors error : values()) {
            mappings.put(error.getValue(), error);
        }
    }

    private VdcBllErrors(int value) {
        intValue = value;
    }

    public int getValue() {
        return intValue;
    }

    public static VdcBllErrors forValue(int value) {
        return mappings.get(value);
    }
}
