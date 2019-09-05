package edu.kit.ipd.parse.vamos.command_classification;

import javafx.util.Pair;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class BinaryNeuralClassifier extends AbstractNeuralClassifier<float[]> {

    private static final Logger logger = LoggerFactory.getLogger(BinaryNeuralClassifier.class);

    public BinaryNeuralClassifier(Properties props) {
        super(props);

        /* load model:
         * the architecture and the weights of the model, the training configuration (loss, optimizer) and their state
         * allowing to resume training exactly where you left off */
        try {
            this.model = KerasModelImport.importKerasSequentialModelAndWeights(modelResource.getFile().getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ModelConfiguration getModelConfig(Properties props) {
        String tokPath = props.getProperty("VOCAB");
        String modelPath = props.getProperty("BIN_MODEL");
        return new ModelConfiguration(tokPath, modelPath);
    }

    @Override
    public Pair<String[], float[]> getTrainingInstances(List<List<String>> records) {
        // save as pair<features, labels>
        String[] input = new String[records.size()];

        float[] output = new float[records.size()];
        for (int i = 0; i < records.size(); i++) {
            input[i] = records.get(i).get(0);
            output[i] = Integer.parseInt(records.get(i).get(1));
        }

        return new Pair<>(input, output);
    }

    @Override
    public INDArray getGoldStandard(float[] input) {
        return Nd4j.create(input, new int[]{input.length, 1});
    }

    @Override
    public float[] getPredictedClasses(INDArray result, int sequenceLength) {
        // binary classification assigns 1 label to the whole sequence (ignore int sequenceLength)
        return result.toFloatVector();
    }

    /**
     * Gives the interpretation of the classification result.
     * 0: class 0 (is ExecutionSequence)
     * 1: class 1 (is TeachingSequence)
     * @param result
     * @return
     */
    public boolean isTeachingSequence(float[] result) {
        // value of the first vector value represents the binary class prediction
        logger.debug("Got prediction: '{}'.",  result[0]);
        return result[0] > 0.5;
    }
}
