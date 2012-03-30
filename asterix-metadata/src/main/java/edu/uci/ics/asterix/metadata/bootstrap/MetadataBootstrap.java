/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.asterix.metadata.bootstrap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import edu.uci.ics.asterix.common.config.DatasetConfig.DatasetType;
import edu.uci.ics.asterix.common.config.DatasetConfig.IndexType;
import edu.uci.ics.asterix.common.context.AsterixAppRuntimeContext;
import edu.uci.ics.asterix.common.context.INodeApplicationState;
import edu.uci.ics.asterix.metadata.IDatasetDetails;
import edu.uci.ics.asterix.metadata.MetadataManager;
import edu.uci.ics.asterix.metadata.MetadataTransactionContext;
import edu.uci.ics.asterix.metadata.api.IMetadataIndex;
import edu.uci.ics.asterix.metadata.api.IMetadataNode;
import edu.uci.ics.asterix.metadata.entities.AsterixBuiltinTypeMap;
import edu.uci.ics.asterix.metadata.entities.Dataset;
import edu.uci.ics.asterix.metadata.entities.Datatype;
import edu.uci.ics.asterix.metadata.entities.Dataverse;
import edu.uci.ics.asterix.metadata.entities.Index;
import edu.uci.ics.asterix.metadata.entities.InternalDatasetDetails;
import edu.uci.ics.asterix.metadata.entities.InternalDatasetDetails.FileStructure;
import edu.uci.ics.asterix.metadata.entities.InternalDatasetDetails.PartitioningStrategy;
import edu.uci.ics.asterix.metadata.entities.Node;
import edu.uci.ics.asterix.metadata.entities.NodeGroup;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.runtime.transaction.TreeResourceManager;
import edu.uci.ics.asterix.transaction.management.exception.ACIDException;
import edu.uci.ics.asterix.transaction.management.service.logging.DataUtil;
import edu.uci.ics.asterix.transaction.management.service.transaction.TransactionManagementConstants.LockManagerConstants.LockMode;
import edu.uci.ics.hyracks.api.application.INCApplicationContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndex;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IndexRegistry;
import edu.uci.ics.hyracks.storage.am.common.frames.LIFOMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.freepage.LinkedListFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.impls.NoOpOperationCallback;
import edu.uci.ics.hyracks.storage.am.common.tuples.TypeAwareTupleWriterFactory;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

/**
 * Initializes the remote metadata storage facilities ("universe") using a
 * MetadataManager that is assumed to be co-located in the same JVM. The
 * metadata universe can be bootstrapped from an existing set of metadata files,
 * or it can be started from scratch, creating all the necessary persistent
 * state. The startUniverse() method is intended to be called as part of
 * application deployment (i.e., inside an NC bootstrap), and similarly
 * stopUniverse() should be called upon application undeployment.
 */
public class MetadataBootstrap {
    private static INodeApplicationState applicationState;

    private static IBufferCache bufferCache;
    private static IFileMapProvider fileMapProvider;
    private static IndexRegistry<IIndex> btreeRegistry;

    private static String metadataNodeName;
    private static String metadataStore;
    private static HashSet<String> nodeNames;
    private static String outputDir;

    private static final Logger LOGGER = Logger.getLogger(MetadataBootstrap.class.getName());

    private static IMetadataIndex[] primaryIndexes;
    private static IMetadataIndex[] secondaryIndexes;

    private static void initLocalIndexArrays() {
        primaryIndexes = new IMetadataIndex[] { MetadataPrimaryIndexes.DATAVERSE_DATASET,
                MetadataPrimaryIndexes.DATASET_DATASET, MetadataPrimaryIndexes.DATATYPE_DATASET,
                MetadataPrimaryIndexes.INDEX_DATASET, MetadataPrimaryIndexes.NODE_DATASET,
                MetadataPrimaryIndexes.NODEGROUP_DATASET, MetadataPrimaryIndexes.FUNCTION_DATASET };
        secondaryIndexes = new IMetadataIndex[] { MetadataSecondaryIndexes.GROUPNAME_ON_DATASET_INDEX,
                MetadataSecondaryIndexes.DATATYPENAME_ON_DATASET_INDEX,
                MetadataSecondaryIndexes.DATATYPENAME_ON_DATATYPE_INDEX };
    }

    public static void startUniverse(AsterixProperties asterixProperities, INCApplicationContext ncApplicationContext)
            throws Exception {
        applicationState = (INodeApplicationState) ncApplicationContext.getApplicationObject();
        AsterixAppRuntimeContext runtimeContext = applicationState.getApplicationRuntimeContext();

        // Initialize static metadata objects, such as record types and metadata
        // index descriptors.
        // The order of these calls is important because the index descriptors
        // rely on the type type descriptors.
        
        MetadataRecordTypes.init();  
        MetadataPrimaryIndexes.init();
        MetadataSecondaryIndexes.init();
        initLocalIndexArrays();

        boolean isNewUniverse = true;

        applicationState.getTransactionProvider().getResourceRepository()
                .registerTransactionalResourceManager(TreeResourceManager.ID, TreeResourceManager.getInstance());

        metadataNodeName = asterixProperities.getMetadataNodeName();
        isNewUniverse = asterixProperities.isNewUniverse();
        metadataStore = asterixProperities.getMetadataStore();
        nodeNames = asterixProperities.getNodeNames();
        // nodeStores = asterixProperity.getStores();

        outputDir = asterixProperities.getOutputDir();
        if (outputDir != null) {
            (new File(outputDir)).mkdirs();
        }

        //btreeRegistry = runtimeContext.getTreeRegistry(); //modified by kisskys
        btreeRegistry = runtimeContext.getIndexRegistry();
        bufferCache = runtimeContext.getBufferCache();
        fileMapProvider = runtimeContext.getFileMapManager();

        // Create fileRefs to all BTree files and open them in BufferCache.
        for (int i = 0; i < primaryIndexes.length; i++) {
            openIndexFile(primaryIndexes[i]);
        }
        for (int i = 0; i < secondaryIndexes.length; i++) {
            openIndexFile(secondaryIndexes[i]);
        }

        // Begin a transaction against the metadata.
        // Lock the metadata in X mode.
        MetadataTransactionContext mdTxnCtx = MetadataManager.INSTANCE.beginTransaction();
        MetadataManager.INSTANCE.lock(mdTxnCtx, LockMode.EXCLUSIVE);

        try {
            if (isNewUniverse) {
                
                //create resourceIdSeed and set to primaryIndexes.length + secondaryIndexes.length + 1
                //The last 1 came from the another static resourceId of MetadataNode, that is, METADATANODE_RESOURCE_ID.
                MetadataManager.INSTANCE.createResourceIdSeed(primaryIndexes.length + secondaryIndexes.length + 1);
                
                for (int i = 0; i < primaryIndexes.length; i++) {
                    //generate resourceId for each resource
                    createIndex(primaryIndexes[i]);
                    registerTransactionalResource(primaryIndexes[i]);
                }
                for (int i = 0; i < secondaryIndexes.length; i++) {
                    //generate resourceId for each resource
                    createIndex(secondaryIndexes[i]);
                    registerTransactionalResource(secondaryIndexes[i]);
                }
                insertInitialDataverses(mdTxnCtx);
                insertInitialDatasets(mdTxnCtx);
                insertInitialDatatypes(mdTxnCtx);
                insertInitialIndexes(mdTxnCtx);
                insertNodes(mdTxnCtx);
                insertInitialGroups(mdTxnCtx);
                LOGGER.info("FINISHED CREATING METADATA B-TREES.");
            } else {
                for (int i = 0; i < primaryIndexes.length; i++) {
                    enlistMetadataDataset(primaryIndexes[i]);
                    registerTransactionalResource(primaryIndexes[i]);
                }
                for (int i = 0; i < secondaryIndexes.length; i++) {
                    enlistMetadataDataset(secondaryIndexes[i]);
                    registerTransactionalResource(secondaryIndexes[i]);
                }
                
                //Check the last value which was generated from the resourceIdSeed.
                //Then, create resourceIdSeed with setting to the last value + 1.
                MetadataManager.INSTANCE.createResourceIdSeed(MetadataManager.INSTANCE.getGeneratedMaxResourceId() + 1);
                
                LOGGER.info("FINISHED ENLISTMENT OF METADATA B-TREES.");
            }
            MetadataManager.INSTANCE.commitTransaction(mdTxnCtx);
        } catch (Exception e) {
            MetadataManager.INSTANCE.abortTransaction(mdTxnCtx);
            throw e;
        }
    }

    public static void stopUniverse() throws HyracksDataException {
        try {
            // Close all BTree files in BufferCache.
            for (int i = 0; i < primaryIndexes.length; i++) {
                bufferCache.closeFile(primaryIndexes[i].getFileId());
            }
            for (int i = 0; i < secondaryIndexes.length; i++) {
                bufferCache.closeFile(secondaryIndexes[i].getFileId());
            }
        } catch (HyracksDataException e) {
            // Ignore for now.
            // TODO: If multiple NCs are running in the same VM, then we could
            // have multiple NCs undeploying asterix concurrently.
            // It would also mean that there is only one BufferCache. A
            // pathological sequence of events would be that NC2
            // closes the BufferCache and then NC1 enters this portion of the
            // code and tries to close unopened files.
            // What we really want is to check whether the BufferCache is open
            // in a synchronized block.
            // The BufferCache api currently does not allow us to check for
            // openness.
            // Swallowing the exceptions is a simple fix for now.
        }
    }

    private static void openIndexFile(IMetadataIndex index) throws HyracksDataException, ACIDException {
        String filePath = metadataStore + index.getFileNameRelativePath();
        FileReference file = new FileReference(new File(filePath));
        bufferCache.createFile(file);
        int fileId = fileMapProvider.lookupFileId(file);
        bufferCache.openFile(fileId);
        index.setFileId(fileId);
    }

    private static void registerTransactionalResource(IMetadataIndex index) throws ACIDException {
        ITreeIndex treeIndex = (ITreeIndex) btreeRegistry.get(DataUtil.byteArrayToInt(index.getResourceId(), 0));
        applicationState.getTransactionProvider().getResourceRepository()
                .registerTransactionalResource(index.getResourceId(), treeIndex);
        index.initTreeLogger();
    }

    public static void insertInitialDataverses(MetadataTransactionContext mdTxnCtx) throws Exception {
        String dataverseName = MetadataPrimaryIndexes.DATAVERSE_DATASET.getDataverseName();
        String dataFormat = "edu.uci.ics.asterix.runtime.formats.NonTaggedDataFormat";
        MetadataManager.INSTANCE.addDataverse(mdTxnCtx, new Dataverse(dataverseName, dataFormat));
    }

    public static void insertInitialDatasets(MetadataTransactionContext mdTxnCtx) throws Exception {
        for (int i = 0; i < primaryIndexes.length; i++) {
            IDatasetDetails id = new InternalDatasetDetails(FileStructure.BTREE, PartitioningStrategy.HASH,
                    primaryIndexes[i].getPartitioningExpr(), primaryIndexes[i].getPartitioningExpr(),
                    primaryIndexes[i].getNodeGroupName());
            MetadataManager.INSTANCE.addDataset(mdTxnCtx, new Dataset(primaryIndexes[i].getDataverseName(),
                    primaryIndexes[i].getIndexedDatasetName(), primaryIndexes[i].getPayloadRecordType().getTypeName(),
                    id, DatasetType.INTERNAL));
        }
    }

    public static void getBuiltinTypes(ArrayList<IAType> types) throws Exception {
        Collection<BuiltinType> builtinTypes = AsterixBuiltinTypeMap.getBuiltinTypes().values();
        Iterator<BuiltinType> iter = builtinTypes.iterator();
        while (iter.hasNext())
            types.add(iter.next());
    }

    public static void getMetadataTypes(ArrayList<IAType> types) throws Exception {
        for (int i = 0; i < primaryIndexes.length; i++)
            types.add(primaryIndexes[i].getPayloadRecordType());
    }

    public static void insertInitialDatatypes(MetadataTransactionContext mdTxnCtx) throws Exception {
        String dataverseName = MetadataPrimaryIndexes.DATAVERSE_DATASET.getDataverseName();
        ArrayList<IAType> types = new ArrayList<IAType>();
        getBuiltinTypes(types);
        getMetadataTypes(types);
        for (int i = 0; i < types.size(); i++) {
            MetadataManager.INSTANCE.addDatatype(mdTxnCtx, new Datatype(dataverseName, types.get(i).getTypeName(),
                    types.get(i), false));
        }
    }

    public static void insertInitialIndexes(MetadataTransactionContext mdTxnCtx) throws Exception {
        for (int i = 0; i < secondaryIndexes.length; i++) {
            MetadataManager.INSTANCE.addIndex(mdTxnCtx, new Index(secondaryIndexes[i].getDataverseName(),
                    secondaryIndexes[i].getIndexedDatasetName(), secondaryIndexes[i].getIndexName(), IndexType.BTREE,
                    secondaryIndexes[i].getPartitioningExpr(), false, MetadataManager.INSTANCE.generateResourceId()));
        }
    }

    public static void insertNodes(MetadataTransactionContext mdTxnCtx) throws Exception {
        Iterator<String> iter = nodeNames.iterator();
        // Set<Entry<String, String[]>> set = nodeStores.entrySet();
        // Iterator<Entry<String, String[]>> im = set.iterator();

        while (iter.hasNext()) {
            // Map.Entry<String, String[]> me = (Map.Entry<String,
            // String[]>)im.next();
            MetadataManager.INSTANCE.addNode(mdTxnCtx, new Node(iter.next(), 0, 0/*
                                                                                 * , me . getValue ( )
                                                                                 */));
        }
    }

    public static void insertInitialGroups(MetadataTransactionContext mdTxnCtx) throws Exception {
        String groupName = MetadataPrimaryIndexes.DATAVERSE_DATASET.getNodeGroupName();
        List<String> metadataGroupNodeNames = new ArrayList<String>();
        metadataGroupNodeNames.add(metadataNodeName);
        NodeGroup groupRecord = new NodeGroup(groupName, metadataGroupNodeNames);
        MetadataManager.INSTANCE.addNodegroup(mdTxnCtx, groupRecord);

        List<String> nodes = new ArrayList<String>();
        nodes.addAll(nodeNames);
        NodeGroup defaultGroup = new NodeGroup(MetadataConstants.METADATA_DEFAULT_NODEGROUP_NAME, nodes);
        MetadataManager.INSTANCE.addNodegroup(mdTxnCtx, defaultGroup);

    }

    public static void createIndex(IMetadataIndex dataset) throws Exception {
        int fileId = dataset.getFileId();
        ITypeTraits[] typeTraits = dataset.getTypeTraits();
        IBinaryComparatorFactory[] comparatorFactories = dataset.getKeyBinaryComparatorFactory();
        TypeAwareTupleWriterFactory tupleWriterFactory = new TypeAwareTupleWriterFactory(typeTraits);
        ITreeIndexFrameFactory leafFrameFactory = new BTreeNSMLeafFrameFactory(tupleWriterFactory);
        ITreeIndexFrameFactory interiorFrameFactory = new BTreeNSMInteriorFrameFactory(tupleWriterFactory);
        ITreeIndexMetaDataFrameFactory metaDataFrameFactory = new LIFOMetaDataFrameFactory();
        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, 0, metaDataFrameFactory);
        freePageManager.open(fileId);
        BTree btree = new BTree(bufferCache, NoOpOperationCallback.INSTANCE, typeTraits.length, comparatorFactories,
                freePageManager, interiorFrameFactory, leafFrameFactory);
        btree.create(fileId);
        btreeRegistry.register(DataUtil.byteArrayToInt(dataset.getResourceId(), 0), btree);
    }

    public static void enlistMetadataDataset(IMetadataIndex dataset) throws Exception {
        int fileId = dataset.getFileId();
        ITypeTraits[] typeTraits = dataset.getTypeTraits();
        IBinaryComparatorFactory[] comparatorFactories = dataset.getKeyBinaryComparatorFactory();

        TypeAwareTupleWriterFactory tupleWriterFactory = new TypeAwareTupleWriterFactory(typeTraits);

        ITreeIndexFrameFactory leafFrameFactory = new BTreeNSMLeafFrameFactory(tupleWriterFactory);
        ITreeIndexFrameFactory interiorFrameFactory = new BTreeNSMInteriorFrameFactory(tupleWriterFactory);

        ITreeIndexMetaDataFrameFactory metaDataFrameFactory = new LIFOMetaDataFrameFactory();
        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, 0, metaDataFrameFactory);
        freePageManager.open(fileId);

        BTree btree = new BTree(bufferCache, NoOpOperationCallback.INSTANCE, typeTraits.length, comparatorFactories,
                freePageManager, interiorFrameFactory, leafFrameFactory);

        btreeRegistry.register(DataUtil.byteArrayToInt(dataset.getResourceId(), 0), btree);
    }

    public static String getOutputDir() {
        return outputDir;
    }

    public static String getMetadataNodeName() {
        return metadataNodeName;
    }

}
