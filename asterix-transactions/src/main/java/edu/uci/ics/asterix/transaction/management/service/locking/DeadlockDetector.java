package edu.uci.ics.asterix.transaction.management.service.locking;

import java.util.HashMap;

import edu.uci.ics.hyracks.api.job.JobId;

/**
 * @author pouria, kisskys Performing a BFS search, upon adding each waiter to a waiting
 *         list to avoid deadlocks this class implements such a loop-detector in
 *         the wait-for-graph
 */

public class DeadlockDetector {

    private HashMap<JobId, JobInfo> jobHT;
    private HashMap<DatasetId, DatasetLockInfo> datasetResourceHT;
    private EntityLockInfoManager entityLockInfoManager;
    private EntityInfoManager entityInfoManager;
    private LockWaiterManager lockWaiterManager;

    private PrimitiveIntHashMap holderList;
    private PrimitiveIntHashMap nextHolderList;
    private PrimitiveIntHashMap resourceList;
    private PrimitiveIntHashMap visitedHolderList;
    private JobId tempJobIdObj; //temporary object to avoid object creation
    private DatasetId tempDatasetIdObj; //temporary object to avoid object creation

    
    public DeadlockDetector(HashMap<JobId, JobInfo> jobHT, HashMap<DatasetId, DatasetLockInfo> datasetResourceHT,
            EntityLockInfoManager entityLockInfoManager, EntityInfoManager entityInfoManager,
            LockWaiterManager lockWaiterManager) {
        this.jobHT = jobHT;
        this.datasetResourceHT = datasetResourceHT;
        this.entityLockInfoManager = entityLockInfoManager;
        this.entityInfoManager = entityInfoManager;
        this.lockWaiterManager = lockWaiterManager;
        holderList = new PrimitiveIntHashMap(1 << 6, 1 << 3, 180000);
        nextHolderList = new PrimitiveIntHashMap(1 << 6, 1 << 3, 180000);
        resourceList = new PrimitiveIntHashMap(1, 1 << 4, 180000);
        visitedHolderList = new PrimitiveIntHashMap(1 << 6, 1 << 3, 180000);
        tempJobIdObj = new JobId(0);
        tempDatasetIdObj = new DatasetId(0);
    }
    
    public boolean isSafeToAdd(DatasetLockInfo dLockInfo, int eLockInfo, int entityInfo, boolean isDatasetLockInfo) {
        int holder;
        int nextHolder;
        int visitedHolder;
        int callerId = entityInfoManager.getJobId(entityInfo);
        int datasetId = entityInfoManager.getDatasetId(entityInfo);
        int hashValue = entityInfoManager.getPKHashVal(entityInfo);
        int resource;
        PrimitiveIntHashMap tempHolderList;

        holderList.clear(true);

        //holderlist contains jobId
        //resourceList contains entityInfo's slot numbers instead of resourceId in order to avoid object creation 
        //since resourceId consists of datasetId and PKHashValue

        //get holder list(jobId list)
        if (isDatasetLockInfo) {
            getHolderList(datasetId, -1, holderList);
        } else {
            getHolderList(datasetId, hashValue, holderList);
        }

        //while holderList is not empty
        holderList.beginIterate();
        holder = holderList.getNextKey();
        while (holder != -1) {

            nextHolderList.clear(true);

            while (holder != -1) {
                getWaitingResourceList(holder, resourceList);
                resourceList.beginIterate();
                resource = resourceList.getNextKey();

                while (resource != -1) {
                    //get dataset holder
                    getHolderList(entityInfoManager.getDatasetId(resource), -1, nextHolderList);
                    //get entity holder
                    getHolderList(entityInfoManager.getDatasetId(resource), entityInfoManager.getPKHashVal(resource),
                            nextHolderList);
                    if (nextHolderList.get(callerId) != -1) {
                        return false;
                    }
                    resource = resourceList.getNextKey();
                }

                visitedHolderList.put(holder, -1);
                holder = holderList.getNextKey();
            }

            //remove visitedHolder for nextHolderList;
            visitedHolderList.beginIterate();
            visitedHolder = visitedHolderList.getNextKey();
            while (visitedHolder != -1) {
                nextHolderList.remove(visitedHolder);
                visitedHolder = visitedHolderList.getNextKey();
            }

            //swap holder list
            //set holderList to nextHolderList and nextHolderList to holderList
            tempHolderList = holderList;
            holderList = nextHolderList;
            nextHolderList = holderList;
            holderList.beginIterate();
            holder = holderList.getNextKey();
        }

        return true;
    }

    /**
     * Get holder list of dataset if hashValue == -1. Get holder list of entity otherwise.
     * Where, a holder is a jobId, not entityInfo's slotNum
     * @param datasetId
     * @param hashValue
     * @param holderList
     */
    private void getHolderList(int datasetId, int hashValue, PrimitiveIntHashMap holderList) {
        PrimitiveIntHashMap entityHT;
        DatasetLockInfo dLockInfo;
        int entityLockInfo;
        int entityInfo;
        
        //get datasetLockInfo
        tempDatasetIdObj.setId(datasetId);
        dLockInfo = datasetResourceHT.get(tempDatasetIdObj);
        if (dLockInfo == null) {
            return;
        }
        
        if (hashValue == -1) {
            //get S/X-lock holders of dataset
            entityInfo = dLockInfo.getLastHolder();
            while(entityInfo != -1) {
                holderList.put(entityInfoManager.getJobId(entityInfo), -1);
                entityInfo = entityInfoManager.getPrevEntityActor(entityInfo);
            }
            
            //get IS/IX-lock holders of dataset
            entityHT = dLockInfo.getEntityResourceHT();
            entityHT.beginIterate();
            entityLockInfo = entityHT.getNextKey();
            while (entityLockInfo != -1) {
                entityInfo = entityLockInfoManager.getLastHolder(entityLockInfo);
                while (entityInfo != -1) {
                    holderList.put(entityInfoManager.getJobId(entityInfo), -1);
                    entityInfo = entityInfoManager.getPrevEntityActor(entityInfo);
                }
                entityLockInfo = entityHT.getNextKey();
            }
        } else {
            //get S/X-lock holders of entity
            entityHT = dLockInfo.getEntityResourceHT();
            entityLockInfo = entityHT.get(hashValue);
            if (entityLockInfo != -1) {
                entityInfo = entityLockInfoManager.getLastHolder(entityLockInfo);
                while (entityInfo != -1) {
                    holderList.put(entityInfoManager.getJobId(entityInfo), -1);
                    entityInfo = entityInfoManager.getPrevEntityActor(entityInfo);
                }
            }
        }
        return;
    }

    /**
     * Get waiting resource list of jobId, where a resource is represented with entityInfo's slot number
     * @param jobId
     * @param resourceList
     */
    private void getWaitingResourceList(int jobId, PrimitiveIntHashMap resourceList) {
        JobInfo jobInfo;
        int waiterId;
        LockWaiter waiterObj;
        int entityInfo;
        
        //get JobInfo
        tempJobIdObj.setId(jobId);
        jobInfo = jobHT.get(tempJobIdObj);
        
        //get WaiterObj
        waiterId = jobInfo.getFirstWaitingResource();
        while (waiterId != -1)
        {
            waiterObj = lockWaiterManager.getLockWaiter(waiterId);
            entityInfo = waiterObj.getEntityInfoSlot();
            resourceList.put(entityInfo, -1);
            waiterId = entityInfoManager.getPrevJobResource(entityInfo);
        }
        return;
    }
}
