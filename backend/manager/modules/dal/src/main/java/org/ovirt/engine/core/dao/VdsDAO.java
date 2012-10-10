package org.ovirt.engine.core.dao;

import java.util.List;

import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSStatus;
import org.ovirt.engine.core.common.businessentities.VDSType;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.compat.NGuid;

/**
 * <code>VdsDAO</code> defines a type that performs CRUD operations on instances of {@link VDS}.
 *
 *
 */
public interface VdsDAO extends DAO, SearchDAO<VDS>, AutoRecoverDAO<VDS> {
    /**
     * Retrieves the instance with the given id.
     *
     * @param id
     *            the id
     * @return the VDS instance
     */
    VDS get(NGuid id);

    /**
     * Retrieves the instance with the given id, with optional permission filtering.
     *
     * @param id
     *            the id
     * @param userID
     *            the ID of the user requesting the information
     * @param isFiltered
     *            Whether the results should be filtered according to the user's permissions
     * @return the VDS instance
     */
    VDS get(NGuid id, Guid userID, boolean isFiltered);

    /**
     * Finds all instances with the given name.
     *
     * @param name
     *            the name
     * @return the list of instances
     */
    List<VDS> getAllWithName(String name);

    /**
     * Finds all instances for the given host.
     *
     * @param hostname
     *            the hostname
     * @return the list of instances
     */
    List<VDS> getAllForHostname(String hostname);

    /**
     * Retrieves all instances with the given address.
     *
     * @param address
     *            the address
     * @return the list of instances
     */
    List<VDS> getAllWithIpAddress(String address);

    /**
     * Retrieves all instances with the given unique id.
     *
     * @param id
     *            the unique id
     * @return the list of instances
     */
    List<VDS> getAllWithUniqueId(String id);

    /**
     * Retrieves all instances for the specified type.
     *
     * @param vds
     *            the type
     * @return the list of instances
     */
    List<VDS> getAllOfType(VDSType vds);

    /**
     * Retrieves all instances for the given list of types.
     *
     * @param types
     *            the type filter
     * @return the list of instances
     */
    List<VDS> getAllOfTypes(VDSType[] types);

    /**
     * Retrieves all instances by group id.
     *
     * @param vdsGroup
     *            the group id
     * @return the list of instances
     */
    List<VDS> getAllForVdsGroupWithoutMigrating(Guid vdsGroup);

    /**
     * Retrieves all VDS instances.
     *
     * @param userID
     *            the ID of the user requesting the information
     * @param isFiltered
     *            Whether the results should be filtered according to the user's permissions
     * @return the list of VDS instances
     */
    List<VDS> getAll(Guid userID, boolean isFiltered);

    /**
     * Retrieves all VDS instances.
     *
     * @return the list of VDS instances
     */
    List<VDS> getAll();

    /**
     * Retrieves all VDS instances by vds group id (cluster ID)
     *
     * @param vdsGroup
     * @return the list of VDS instances
     */
    List<VDS> getAllForVdsGroup(Guid vdsGroup);

    /**
     * Retrieves all VDS instances by vds group id (cluster ID) with optional filtering
     *
     * @param vdsGroup
     * @param userID
     *            the ID of the user requesting the information
     * @param isFiltered
     *            Whether the results should be filtered according to the user's permissions
     * @return the list of VDS instances
     */
    List<VDS> getAllForVdsGroup(Guid vdsGroup, Guid userID, boolean isFiltered);

    /**
     * Retrieves all VDS instances by storage pool ID.
     *
     * @param storagePool The storage pool's ID
     * @return the list of VDS instances
     */
    List<VDS> getAllForStoragePool(Guid storagePool);

    /**
     * Retrieves all VDS instances by storage pool ID, with optional filtering
     *
     * @param storagePool The storage pool's ID
     * @param userID
     *            the ID of the user requesting the information
     * @param isFiltered
     *            Whether the results should be filtered according to the user's permissions
     * @return the list of VDS instances
     */
    List<VDS> getAllForStoragePool(Guid storagePool, Guid userID, boolean isFiltered);

    /**
     * Retrieves all VDS instances by storage pool ID and status.
     *
     * @param storagePool The storage pool's ID
     * @param status The status of vds
     * @return the list of VDS instances
     */
    List<VDS> getAllForStoragePoolAndStatus(Guid storagePool, VDSStatus status);

    /**
     * Retrieves all VDS instances in the given Storage Pool, that are in status "UP"
     * ordered by their vds_spm_priority, not including -1
     * @return the list of VDS instances
     */
    List<VDS> getListForSpmSelection(Guid storagePoolId);

    /**
     * Retrieves all VDS instances in the given Vds group, that are in given status
     * @param vdsGroupId
     * @param status
     * @return list of VDS instances
     */
    List<VDS> getAllForVdsGroupWithStatus(Guid vdsGroupId, VDSStatus status);
}
