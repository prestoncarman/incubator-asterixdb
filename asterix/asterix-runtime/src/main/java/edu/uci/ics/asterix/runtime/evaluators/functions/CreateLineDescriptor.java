package edu.uci.ics.asterix.runtime.evaluators.functions;

import java.io.DataOutput;
import java.io.IOException;

import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.dataflow.data.nontagged.Coordinate;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.ADoubleSerializerDeserializer;
import edu.uci.ics.asterix.dataflow.data.nontagged.serde.APointSerializerDeserializer;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ALine;
import edu.uci.ics.asterix.om.base.AMutableLine;
import edu.uci.ics.asterix.om.base.AMutablePoint;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class CreateLineDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    public final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS, "create-line", 2,
            true);
    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        public IFunctionDescriptor createFunctionDescriptor() {
            return new CreateLineDescriptor();
        }
    };

    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) throws AlgebricksException {
        return new ICopyEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public ICopyEvaluator createEvaluator(final IDataOutputProvider output) throws AlgebricksException {
                return new ICopyEvaluator() {

                    private DataOutput out = output.getDataOutput();

                    private ArrayBackedValueStorage outInput0 = new ArrayBackedValueStorage();
                    private ArrayBackedValueStorage outInput1 = new ArrayBackedValueStorage();
                    private ICopyEvaluator eval0 = args[0].createEvaluator(outInput0);
                    private ICopyEvaluator eval1 = args[1].createEvaluator(outInput1);
                    private AMutableLine aLine = new AMutableLine(null, null);
                    private AMutablePoint[] aPoint = { new AMutablePoint(0, 0), new AMutablePoint(0, 0) };
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ALine> lineSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ALINE);

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        outInput0.reset();
                        eval0.evaluate(tuple);
                        outInput1.reset();
                        eval1.evaluate(tuple);

                        try {
                            aPoint[0].setValue(ADoubleSerializerDeserializer.getDouble(outInput0.getByteArray(),
                                    APointSerializerDeserializer.getCoordinateOffset(Coordinate.X)),
                                    ADoubleSerializerDeserializer.getDouble(outInput0.getByteArray(),
                                            APointSerializerDeserializer.getCoordinateOffset(Coordinate.Y)));
                            aPoint[1].setValue(ADoubleSerializerDeserializer.getDouble(outInput1.getByteArray(),
                                    APointSerializerDeserializer.getCoordinateOffset(Coordinate.X)),
                                    ADoubleSerializerDeserializer.getDouble(outInput1.getByteArray(),
                                            APointSerializerDeserializer.getCoordinateOffset(Coordinate.Y)));
                            aLine.setValue(aPoint[0], aPoint[1]);
                            lineSerde.serialize(aLine, out);
                        } catch (IOException e1) {
                            throw new AlgebricksException(e1);
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