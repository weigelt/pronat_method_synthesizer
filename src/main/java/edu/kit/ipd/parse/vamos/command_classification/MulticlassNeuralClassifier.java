package edu.kit.ipd.parse.vamos.command_classification;

import edu.kit.ipd.parse.vamos.utils.ExternalScriptCallUtils;
import edu.stanford.nlp.math.ArrayMath;
import javafx.util.Pair;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MulticlassNeuralClassifier extends AbstractNeuralClassifier<List<String[]>> {

    private static final Logger logger = LoggerFactory.getLogger(MulticlassNeuralClassifier.class);

    public MulticlassNeuralClassifier(Properties props) {
        super(props);

        // if Deeplearning4J CAN load model layers
        if (Boolean.valueOf(props.getProperty("USE_INTERAL_MCLASS_MODEL"))) {
            /* load model:
             * the architecture and the weights of the model, the training configuration (loss, optimizer) and their state
             * allowing to resume training exactly where you left off */
            try {
                this.model = KerasModelImport.importKerasSequentialModelAndWeights(modelResource.getFile().getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }

        // else use external python script
        } else {
            new ExternalScriptCallUtils();
            ExternalScriptCallUtils.checkForPythonEnv();
        }
    }

    @Override
    public ModelConfiguration getModelConfig(Properties props) {
        String tokPath = props.getProperty("VOCAB");
        String modelPath = props.getProperty("MCLASS_MODEL");
        return new ModelConfiguration(tokPath, modelPath);
    }

    /**
     * Only for multiclass classification
     * Transform labels by encoding with integers and padding to MAXIMUM_SEQ_LENGTH
     * @param y_labels labels
     * @return encoded and padded labels
     */
    @Override
    public INDArray getGoldStandard(List<String[]> y_labels) {
        float[][] y_padded = new float[y_labels.size()][SEQUENCE_LENGTH];
        for (int i = 0; i < y_labels.size(); i++) {
            for (int j = 0; j < SEQUENCE_LENGTH; j++) {
                if (j < y_labels.get(i).length) {
                    y_padded[i][j] = MulticlassLabels.valueOf(y_labels.get(i)[j]).getId();
                } else {
                    y_padded[i][j] = 0;
                }
            }
        }

        return Nd4j.create(y_padded);
    }

    @Override
    public List<String[]> getPredictedClasses(INDArray result, int sequenceLength) {
        double[][] resultMatrix = result.toDoubleMatrix();
        List<String[]> labelResult = new ArrayList<>();

        // multiclass classification assigns sequenceLength labels (one for each word if the sequence)
        for (int i = 0; i < sequenceLength; i++) {
            double[] resultVector = resultMatrix[i];
            labelResult.add(new String[] { decodeNumericToLabel(ArrayMath.argmax(resultVector)).toString() });
        }

        return labelResult;
    }

    @Override
    public Pair<String[], List<String[]>> getTrainingInstances(List<List<String>> records) {
        // save as pair<features, labels>
        String[] input = new String[records.size()];

        List<String[]> output = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            input[i] = records.get(i).get(0);
            output.add(records.get(i).get(1).split(","));
        }

        return new Pair<>(input, output);
    }

    public List<MulticlassLabels> getInterpretedPredictedLabels(INDArray result, int sequenceLength) {
        double[][] resultMatrix = result.toDoubleMatrix();
        List<MulticlassLabels> labelResult = new ArrayList<>();

        for (int i = 0; i < sequenceLength; i++) {
            double[] resultVector = resultMatrix[i];
            labelResult.add(decodeNumericToLabel(ArrayMath.argmax(resultVector)));
        }

        logger.debug("Got prediction: '{}'", labelResult.toString());
        return labelResult;
    }

    private MulticlassLabels decodeNumericToLabel(int numericResult) {
        // resultVector of label probabilities in format [PAD, DECL, DESC, ELSE]
        MulticlassLabels label = MulticlassLabels.getLabelLookup().get(numericResult);
        if (label == null) {
            logger.error("Classifier predicted invalid label");
            label = MulticlassLabels.DESC;
        }
        return label;
    }

    public INDArray getExternalMclassModelSinglePrediction(float[] tokenized_input, int inputLength) {
        logger.debug("Predict with {}", this.getClass());

        String mclassPath = null;
        try {
            mclassPath = getModelPath().getFile().getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Could not find mclass model.");
        }

        String mclassPredictionOutput = ExternalScriptCallUtils.runExternalScript(mclassPath, tokenized_input, inputLength);
        if (mclassPredictionOutput.isEmpty()) logger.error("Error occured in classification, clfOutput.txt is empty.");

        return Nd4j.create(ExternalScriptCallUtils.parseJson(mclassPredictionOutput, inputLength));
    }



}
