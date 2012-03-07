package edu.uci.ics.asterix.om.typecomputer.impl;

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.asterix.om.typecomputer.base.IResultTypeComputer;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.AUnionType;
import edu.uci.ics.asterix.om.types.AbstractCollectionType;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.om.types.IAType;
import edu.uci.ics.asterix.om.util.NonTaggedFormatUtil;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;

public class NonTaggedCollectionMemberResultType implements IResultTypeComputer {

    public static final NonTaggedCollectionMemberResultType INSTANCE = new NonTaggedCollectionMemberResultType();

    private NonTaggedCollectionMemberResultType() {
    }

    @Override
    public IAType computeType(ILogicalExpression expression, IVariableTypeEnvironment env,
            IMetadataProvider<?, ?> metadataProvider) throws AlgebricksException {
        AbstractFunctionCallExpression f = (AbstractFunctionCallExpression) expression;
        IAType type = (IAType) env.getType(f.getArguments().get(0).getValue());
        if (type.getTypeTag() == ATypeTag.UNION && NonTaggedFormatUtil.isOptionalField((AUnionType) type))
            type = ((AUnionType) type).getUnionList().get(NonTaggedFormatUtil.OPTIONAL_TYPE_INDEX_IN_UNION_LIST);
        if (type.getTypeTag() == ATypeTag.ANY)
            return BuiltinType.ANY;
        else {
            if (((AbstractCollectionType) type).getItemType().getTypeTag() == ATypeTag.NULL)
                return BuiltinType.ANULL;
            List<IAType> unionList = new ArrayList<IAType>();
            unionList.add(BuiltinType.ANULL);
            unionList.add(((AbstractCollectionType) type).getItemType());
            return new AUnionType(unionList, "CollectionMemberResult");
        }
    }

}