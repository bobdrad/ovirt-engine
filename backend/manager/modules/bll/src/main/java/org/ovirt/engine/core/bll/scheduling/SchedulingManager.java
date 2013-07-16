package org.ovirt.engine.core.bll.scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.ovirt.engine.core.common.businessentities.MigrationSupport;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSGroup;
import org.ovirt.engine.core.common.businessentities.VDSType;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.scheduling.ClusterPolicy;
import org.ovirt.engine.core.common.scheduling.PolicyUnit;
import org.ovirt.engine.core.common.utils.Pair;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.dao.VdsDAO;
import org.ovirt.engine.core.dao.VdsDynamicDAO;
import org.ovirt.engine.core.dao.VdsGroupDAO;
import org.ovirt.engine.core.dao.scheduling.ClusterPolicyDao;
import org.ovirt.engine.core.dao.scheduling.PolicyUnitDao;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;
import org.ovirt.engine.core.utils.timer.OnTimerMethodAnnotation;
import org.ovirt.engine.core.utils.timer.SchedulerUtilQuartzImpl;

public class SchedulingManager {
    private static Log log = LogFactory.getLog(SchedulingManager.class);
    /**
     * singleton
     */
    private static SchedulingManager instance = null;

    public static SchedulingManager getInstance() {
        if (instance == null) {
            synchronized (SchedulingManager.class) {
                if (instance == null) {
                    instance = new SchedulingManager();
                    EnableLoadBalancer();
                }
            }
        }
        return instance;
    }

    /**
     * <policy id, policy> map
     */
    private final ConcurrentHashMap<Guid, ClusterPolicy> policyMap;
    /**
     * <policy unit id, policy unit> map
     */
    private final ConcurrentHashMap<Guid, PolicyUnitImpl> policyUnits;

    private final ConcurrentHashMap<Guid, Object> clusterLockMap = new ConcurrentHashMap<Guid, Object>();

    private final VdsFreeMemoryChecker noWaitingMemoryChecker = new VdsFreeMemoryChecker(new NonWaitingDelayer());
    private MigrationHandler migrationHandler;

    private SchedulingManager() {
        policyMap = new ConcurrentHashMap<Guid, ClusterPolicy>();
        policyUnits = new ConcurrentHashMap<Guid, PolicyUnitImpl>();

        init();
    }

    private void init() {
        loadPolicyUnits();
        loadClusterPolicies();
    }

    public List<ClusterPolicy> getClusterPolicies() {
        return new ArrayList<ClusterPolicy>(policyMap.values());
    }

    public ClusterPolicy getClusterPolicy(Guid clusterPolicyId) {
        return policyMap.get(clusterPolicyId);
    }

    public ClusterPolicy getClusterPolicy(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultClusterPolicy();
        }
        for (ClusterPolicy clusterPolicy : policyMap.values()) {
            if (clusterPolicy.getName().toLowerCase().equals(name.toLowerCase())) {
                return clusterPolicy;
            }
        }
        return null;
    }

    private ClusterPolicy getDefaultClusterPolicy() {
        for (ClusterPolicy clusterPolicy : policyMap.values()) {
            if (clusterPolicy.isDefaultPolicy()) {
                return clusterPolicy;
            }
        }
        return null;
    }

    public List<VDSGroup> getClustersByClusterPolicyId(Guid clusterPolicyId) {
        return getVdsGroupDao().getClustersByClusterPolicyId(clusterPolicyId);
    }

    public Map<Guid, PolicyUnitImpl> getPolicyUnitsMap() {
        return policyUnits;
    }

    protected void loadClusterPolicies() {
        List<ClusterPolicy> allClusterPolicies = getClusterPolicyDao().getAll();
        for (ClusterPolicy clusterPolicy : allClusterPolicies) {
            policyMap.put(clusterPolicy.getId(), clusterPolicy);
        }
    }

    public void setMigrationHandler(MigrationHandler migrationHandler) {
        if (this.migrationHandler != null) {
            throw new RuntimeException("Load balance migration handler should be set only once");
        }
        this.migrationHandler = migrationHandler;
    }

    protected void loadPolicyUnits() {
        List<PolicyUnit> allPolicyUnits = getPolicyUnitDao().getAll();
        for (PolicyUnit policyUnit : allPolicyUnits) {
            policyUnits.put(policyUnit.getId(), PolicyUnitImpl.getPolicyUnitImpl(policyUnit));
        }
    }

    public Guid schedule(VDSGroup cluster,
            VM vm,
            List<Guid> hostBlackList,
            List<Guid> hostWhiteList,
            Guid destHostId,
            List<String> messages,
            VdsFreeMemoryChecker memoryChecker) {
        clusterLockMap.putIfAbsent(cluster.getId(), new Object());
        synchronized (clusterLockMap.get(cluster.getId())) {
            List<VDS> vdsList = getVdsDAO()
                    .getAllOfTypes(new VDSType[] { VDSType.VDS, VDSType.oVirtNode });
            updateInitialHostList(vdsList, hostBlackList, true);
            updateInitialHostList(vdsList, hostWhiteList, false);
            ClusterPolicy policy = policyMap.get(cluster.getClusterPolicyId());
            Map<String, Object> parameters = createClusterPolicyParameters(cluster, vm);
            if (destHostId != null) {
                if (checkDestinationHost(vm,
                        vdsList,
                        destHostId,
                        messages,
                        policy,
                        parameters,
                        memoryChecker)) {
                    return destHostId;
                } else if (vm.getMigrationSupport() == MigrationSupport.PINNED_TO_HOST) {
                    return null;
                }
            }
            vdsList =
                    runFilters(policy.getFilters(),
                            vdsList,
                            parameters,
                            policy.getFilterPositionMap(),
                            messages,
                            memoryChecker);
            if (vdsList == null || vdsList.size() == 0) {
                return null;
            }
            if (policy.getFunctions() == null || policy.getFunctions().isEmpty()) {
                return vdsList.get(0).getId();
            }
            Guid bestHost = runFunctions(policy.getFunctions(), vdsList, parameters);
            if (bestHost != null) {
                getVdsDynamicDao().updatePartialVdsDynamicCalc(
                        bestHost,
                        1,
                        vm.getNumOfCpus(),
                        vm.getMinAllocatedMem(),
                        vm.getVmMemSizeMb(),
                        vm.getNumOfCpus());
            }
            return bestHost;
        }
    }

    public boolean canSchedule(VDSGroup cluster,
            VM vm,
            List<Guid> vdsBlackList,
            List<Guid> vdsWhiteList,
            Guid destVdsId,
            List<String> messages) {
        List<VDS> vdsList = getVdsDAO()
                .getAllOfTypes(new VDSType[] { VDSType.VDS, VDSType.oVirtNode });
        updateInitialHostList(vdsList, vdsBlackList, true);
        updateInitialHostList(vdsList, vdsWhiteList, false);
        ClusterPolicy policy = policyMap.get(cluster.getClusterPolicyId());
        Map<String, Object> parameters = createClusterPolicyParameters(cluster, vm);
        if (destVdsId != null) {
            if (checkDestinationHost(vm,
                    vdsList,
                    destVdsId,
                    messages,
                    policy,
                    parameters,
                    noWaitingMemoryChecker)) {
                return true;
            } else if (vm.getMigrationSupport() == MigrationSupport.PINNED_TO_HOST) {
                return false;
            }
        }
        vdsList =
                runFilters(policy.getFilters(),
                        vdsList,
                        parameters,
                        policy.getFilterPositionMap(),
                        messages,
                        noWaitingMemoryChecker);
        if (vdsList == null || vdsList.size() == 0) {
            return false;
        }
        return true;
    }

    protected boolean checkDestinationHost(VM vm,
            List<VDS> vdsList,
            Guid destVdsId,
            List<String> messages,
            ClusterPolicy policy,
            Map<String, Object> parameters,
            VdsFreeMemoryChecker memoryChecker) {
        List<VDS> destVdsList = new ArrayList<VDS>();
        for (VDS vds : vdsList) {
            if (vds.getId().equals(destVdsId)) {
                destVdsList.add(vds);
                break;
            }
        }
        destVdsList =
                runFilters(policy.getFilters(),
                        destVdsList,
                        parameters,
                        policy.getFilterPositionMap(),
                        messages,
                        memoryChecker);
        return destVdsList != null && destVdsList.size() == 1;
    }

    protected Map<String, Object> createClusterPolicyParameters(VDSGroup cluster, VM vm) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (vm != null) {
            parameters.put(PolicyUnitImpl.VM, vm);
        }
        if (cluster.getClusterPolicyProperties() != null) {
            parameters.putAll(cluster.getClusterPolicyProperties());
        }
        return parameters;
    }

    protected void updateInitialHostList(List<VDS> vdsList, List<Guid> list, boolean contains) {
        if (list != null && list.size() > 0) {
            List<VDS> toRemoveList = new ArrayList<VDS>();
            Set<Guid> listSet = new HashSet<Guid>(list);
            for (VDS vds : vdsList) {
                if (listSet.contains(vds.getId()) == contains) {
                    toRemoveList.add(vds);
                }
            }
            vdsList.removeAll(toRemoveList);
        }
    }

    private List<VDS> runFilters(ArrayList<Guid> filters,
            List<VDS> hostList,
            Map<String, Object> parameters,
            Map<Guid, Integer> filterPositionMap,
            List<String> messages, VdsFreeMemoryChecker memoryChecker) {
        if (filters != null) {
            sortFilters(filters, filterPositionMap);
            for (Guid filter : filters) {
                if (hostList == null || hostList.isEmpty()) {
                    break;
                }
                PolicyUnitImpl filterPolicyUnit = policyUnits.get(filter);
                filterPolicyUnit.setMemoryChecker(memoryChecker);
                hostList = filterPolicyUnit.filter(hostList, parameters, messages);
            }
        }
        return hostList;
    }

    private void sortFilters(ArrayList<Guid> filters, final Map<Guid, Integer> filterPositionMap) {
        if (filterPositionMap == null) {
            return;
        }
        Collections.sort(filters, new Comparator<Guid>() {
            @Override
            public int compare(Guid filter1, Guid filter2) {
                Integer position1 = getPosition(filterPositionMap.get(filter1));
                Integer position2 = getPosition(filterPositionMap.get(filter2));
                return position1 - position2;
            }

            private Integer getPosition(Integer position) {
                if (position == null) {
                    position = 0;
                }
                return position;
            }
        });
    }

    protected Guid runFunctions(ArrayList<Pair<Guid, Integer>> functions,
            List<VDS> hostList,
            Map<String, Object> parameters) {
        Map<Guid, Integer> hostCostTable = new HashMap<Guid, Integer>();
        for (Pair<Guid, Integer> pair : functions) {
            List<Pair<Guid, Integer>> scoreResult = policyUnits.get(pair.getFirst()).score(hostList, parameters);
            for (Pair<Guid, Integer> result : scoreResult) {
                Guid hostId = result.getFirst();
                if (hostCostTable.get(hostId) == null) {
                    hostCostTable.put(hostId, 0);
                }
                hostCostTable.put(hostId,
                        hostCostTable.get(hostId) + pair.getSecond() * result.getSecond());
            }
        }
        Entry<Guid, Integer> bestHostEntry = null;
        for (Entry<Guid, Integer> entry : hostCostTable.entrySet()) {
            if (bestHostEntry == null || bestHostEntry.getValue() > entry.getValue()) {
                bestHostEntry = entry;
            }
        }
        if (bestHostEntry == null) {
            return null;
        }
        return bestHostEntry.getKey();
    }

    public Map<String, String> getCustomPropertiesRegexMap(ClusterPolicy clusterPolicy) {
        Set<Guid> usedPolicyUnits = new HashSet<Guid>();
        if (clusterPolicy.getFilters() != null) {
            usedPolicyUnits.addAll(clusterPolicy.getFilters());
        }
        if (clusterPolicy.getFunctions() != null) {
            for (Pair<Guid, Integer> pair : clusterPolicy.getFunctions()) {
                usedPolicyUnits.add(pair.getFirst());
            }
        }
        if (clusterPolicy.getBalance() != null) {
            usedPolicyUnits.add(clusterPolicy.getBalance());
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Guid policyUnitId : usedPolicyUnits) {
            map.putAll(policyUnits.get(policyUnitId).getParameterRegExMap());
        }
        return map;
    }

    public void addClusterPolicy(ClusterPolicy clusterPolicy) {
        getClusterPolicyDao().save(clusterPolicy);
        policyMap.put(clusterPolicy.getId(), clusterPolicy);
    }

    public void editClusterPolicy(ClusterPolicy clusterPolicy) {
        getClusterPolicyDao().update(clusterPolicy);
        policyMap.put(clusterPolicy.getId(), clusterPolicy);
    }

    public void removeClusterPolicy(Guid clusterPolicyId) {
        getClusterPolicyDao().remove(clusterPolicyId);
        policyMap.remove(clusterPolicyId);
    }

    protected VdsDAO getVdsDAO() {
        return DbFacade.getInstance().getVdsDao();
    }

    protected VdsGroupDAO getVdsGroupDao() {
        return DbFacade.getInstance().getVdsGroupDao();
    }

    protected VdsDynamicDAO getVdsDynamicDao() {
        return DbFacade.getInstance().getVdsDynamicDao();
    }

    protected PolicyUnitDao getPolicyUnitDao() {
        return DbFacade.getInstance().getPolicyUnitDao();
    }

    protected ClusterPolicyDao getClusterPolicyDao() {
        return DbFacade.getInstance().getClusterPolicyDao();
    }

    public static void EnableLoadBalancer() {
        if (Config.<Boolean> GetValue(ConfigValues.EnableVdsLoadBalancing)) {
            log.info("Start scheduling to enable vds load balancer");
            SchedulerUtilQuartzImpl.getInstance().scheduleAFixedDelayJob(instance,
                    "PerformLoadBalancing",
                    new Class[] {},
                    new Object[] {},
                    Config.<Integer> GetValue(ConfigValues.VdsLoadBalancingeIntervalInMinutes),
                    Config.<Integer> GetValue(ConfigValues.VdsLoadBalancingeIntervalInMinutes),
                    TimeUnit.MINUTES);
            log.info("Finished scheduling to enable vds load balancer");
        }
    }

    @OnTimerMethodAnnotation("PerformLoadBalancing")
    public void PerformLoadBalancing() {
        log.debugFormat("Load Balancer timer entered.");
        List<VDSGroup> clusters = DbFacade.getInstance().getVdsGroupDao().getAll();
        for (VDSGroup cluster : clusters) {
            ClusterPolicy policy = policyMap.get(cluster.getClusterPolicyId());
            PolicyUnitImpl policyUnit = policyUnits.get(policy.getBalance());
            List<VDS> hosts = getVdsDAO()
                    .getAllOfTypes(new VDSType[] { VDSType.VDS, VDSType.oVirtNode });
            Pair<List<Guid>, Guid> pair = policyUnit.balance(cluster,
                    hosts,
                    cluster.getClusterPolicyProperties(),
                    new ArrayList<String>());
            if (pair != null && pair.getSecond() != null) {
                migrationHandler.migrateVM((ArrayList<Guid>) pair.getFirst(), pair.getSecond());
            }
        }
    }

}