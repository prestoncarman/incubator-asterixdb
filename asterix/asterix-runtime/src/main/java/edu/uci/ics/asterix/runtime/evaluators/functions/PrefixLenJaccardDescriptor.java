package edu.uci.ics.asterix.runtime.evaluators.functions;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.AFloatSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.AInt32;
import edu.uci.ics.asterix.om.base.AMutableInt32;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.fuzzyjoin.similarity.SimilarityFiltersJaccard;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;

public class PrefixLenJaccardDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS,
            "prefix-len-jaccard", 2, true);

    @Override
    public IEvaluatorFactory createEvaluatorFactory(final IEvaluatorFactory[] args) throws AlgebricksException {

        return new IEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public IEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {

                return new IEvaluator() {

                    private final DataOutput out = output.getDataOutput();
                    private final ArrayBackedValueStorage inputVal = new ArrayBackedValueStorage();
                    private final IEvaluator evalLen = args[0].createEvaluator(inputVal);
                    private final IEvaluator evalThreshold = args[1].createEvaluator(inputVal);

                    private float similarityThresholdCache;
                    private SimilarityFiltersJaccard similarityFilters;

                    // result
                    private final AMutableInt32 res = new AMutableInt32(0);
                    @SuppressWarnings("unchecked")
                    private final ISerializerDeserializer<AInt32> int32Serde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.AINT32);

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        // length
                        inputVal.reset();
                        evalLen.evaluate(tuple);
                        int length = IntegerSerializerDeserializer.getInt(inputVal.getBytes(), 1);

                        // similarity threshold
                        inputVal.reset();
                        evalThreshold.evaluate(tuple);
                        float similarityThreshold = (float) AFloatSerializerDeserializer.getFloat(inputVal.getBytes(),
                                1);

                        if (similarityThreshold != similarityThresholdCache || similarityFilters == null) {
                            similarityFilters = new SimilarityFiltersJaccard(similarityThreshold);
                        }

                        int prefixLength = similarityFilters.getPrefixLength(length);
                        res.setValue(prefixLength);

                        try {
                            int32Serde.serialize(res, out);
                        } catch (IOException e) {
                            throw new AlgebricksException(e);
                        }
                    }
                };
            }
        };
    }

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

}
