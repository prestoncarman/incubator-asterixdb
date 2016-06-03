/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.algebricks.core.algebra.operators.physical;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.base.IHyracksJobBuilder;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import org.apache.hyracks.algebricks.core.algebra.base.LogicalVariable;
import org.apache.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractBinaryJoinOperator.JoinKind;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import org.apache.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder.OrderKind;
import org.apache.hyracks.algebricks.core.algebra.properties.ILocalStructuralProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.IPartitioningProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.IPartitioningRequirementsCoordinator;
import org.apache.hyracks.algebricks.core.algebra.properties.IPhysicalPropertiesVector;
import org.apache.hyracks.algebricks.core.algebra.properties.LocalOrderProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.OrderColumn;
import org.apache.hyracks.algebricks.core.algebra.properties.OrderedPartitionedProperty;
import org.apache.hyracks.algebricks.core.algebra.properties.PhysicalRequirements;
import org.apache.hyracks.algebricks.core.algebra.properties.StructuralPropertiesVector;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import org.apache.hyracks.algebricks.core.jobgen.impl.JobGenHelper;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;
import org.apache.hyracks.api.job.IOperatorDescriptorRegistry;
import org.apache.hyracks.dataflow.common.data.partition.range.IRangeMap;
import org.apache.hyracks.dataflow.common.data.partition.range.IRangePartitionType.RangePartitioningType;
import org.apache.hyracks.dataflow.std.join.IMergeJoinCheckerFactory;
import org.apache.hyracks.dataflow.std.join.MergeJoinOperatorDescriptor;

public class MergeJoinPOperator extends AbstractJoinPOperator {

    private final int memSizeInFrames;
    protected final List<LogicalVariable> keysLeftBranch;
    protected final List<LogicalVariable> keysRightBranch;
    private final IMergeJoinCheckerFactory mjcf;
    private IRangeMap rangeMap;

    private static final Logger LOGGER = Logger.getLogger(MergeJoinPOperator.class.getName());

    public MergeJoinPOperator(JoinKind kind, JoinPartitioningType partitioningType, List<LogicalVariable> sideLeft,
            List<LogicalVariable> sideRight, int memSizeInFrames, IMergeJoinCheckerFactory mjcf, IRangeMap rangeMap) {
        super(kind, partitioningType);
        this.memSizeInFrames = memSizeInFrames;
        this.keysLeftBranch = sideLeft;
        this.keysRightBranch = sideRight;
        this.mjcf = mjcf;
        this.rangeMap = rangeMap;

        LOGGER.fine("MergeJoinPOperator constructed with: JoinKind=" + kind + ", JoinPartitioningType="
                + partitioningType + ", List<LogicalVariable>=" + keysLeftBranch + ", List<LogicalVariable>="
                + keysRightBranch + ", int memSizeInFrames=" + memSizeInFrames + ", IMergeJoinCheckerFactory mjcf="
                + mjcf + ", IRangeMap rangeMap=" + rangeMap + ".");
    }

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.MERGE_JOIN;
    }

    @Override
    public boolean isMicroOperator() {
        return false;
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator iop, IOptimizationContext context) {
        ArrayList<OrderColumn> order = new ArrayList<>();
        for (LogicalVariable v : keysLeftBranch) {
            order.add(new OrderColumn(v, mjcf.isOrderAsc() ? OrderKind.ASC : OrderKind.DESC));
        }
        IPartitioningProperty pp = new OrderedPartitionedProperty(order, null, rangeMap, RangePartitioningType.PROJECT);
        List<ILocalStructuralProperty> propsLocal = new ArrayList<>();
        propsLocal.add(new LocalOrderProperty(order));
        deliveredProperties = new StructuralPropertiesVector(pp, propsLocal);
    }

    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator iop,
            IPhysicalPropertiesVector reqdByParent, IOptimizationContext context) {
        StructuralPropertiesVector[] pv = new StructuralPropertiesVector[2];
        AbstractLogicalOperator op = (AbstractLogicalOperator) iop;

        IPartitioningProperty ppLeft = null;
        List<ILocalStructuralProperty> ispLeft = new ArrayList<>();
        IPartitioningProperty ppRight = null;
        List<ILocalStructuralProperty> ispRight = new ArrayList<>();

        ArrayList<OrderColumn> orderLeft = new ArrayList<>();
        for (LogicalVariable v : keysLeftBranch) {
            orderLeft.add(new OrderColumn(v, mjcf.isOrderAsc() ? OrderKind.ASC : OrderKind.DESC));
        }
        ispLeft.add(new LocalOrderProperty(orderLeft));

        ArrayList<OrderColumn> orderRight = new ArrayList<>();
        for (LogicalVariable v : keysRightBranch) {
            orderRight.add(new OrderColumn(v, mjcf.isOrderAsc() ? OrderKind.ASC : OrderKind.DESC));
        }
        ispRight.add(new LocalOrderProperty(orderRight));

        if (op.getExecutionMode() == AbstractLogicalOperator.ExecutionMode.PARTITIONED) {
            ppLeft = new OrderedPartitionedProperty(orderLeft, null, rangeMap, mjcf.getLeftPartitioningType());
            ppRight = new OrderedPartitionedProperty(orderRight, null, rangeMap, mjcf.getRightPartitioningType());
        }

        pv[0] = new StructuralPropertiesVector(ppLeft, ispLeft);
        pv[1] = new StructuralPropertiesVector(ppRight, ispRight);
        IPartitioningRequirementsCoordinator prc = IPartitioningRequirementsCoordinator.NO_COORDINATION;
        return new PhysicalRequirements(pv, prc);
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        int[] keysLeft = JobGenHelper.variablesToFieldIndexes(keysLeftBranch, inputSchemas[0]);
        int[] keysRight = JobGenHelper.variablesToFieldIndexes(keysRightBranch, inputSchemas[1]);

        IOperatorDescriptorRegistry spec = builder.getJobSpec();
        RecordDescriptor recordDescriptor = JobGenHelper.mkRecordDescriptor(context.getTypeEnvironment(op), opSchema,
                context);

        MergeJoinOperatorDescriptor opDesc = new MergeJoinOperatorDescriptor(spec, memSizeInFrames, recordDescriptor,
                keysLeft, keysRight, mjcf);
        contributeOpDesc(builder, (AbstractLogicalOperator) op, opDesc);

        ILogicalOperator src1 = op.getInputs().get(0).getValue();
        builder.contributeGraphEdge(src1, 0, op, 0);
        ILogicalOperator src2 = op.getInputs().get(1).getValue();
        builder.contributeGraphEdge(src2, 0, op, 1);
    }
}