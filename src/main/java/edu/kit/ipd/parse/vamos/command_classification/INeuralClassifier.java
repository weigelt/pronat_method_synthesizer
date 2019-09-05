package edu.kit.ipd.parse.vamos.command_classification;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * This interface represents neural classifiers working on the input sequence.
 *
 * Expects input which is lowercase, has no numbers ("1" -> "one", "1." -> "first") and no punctuation.
 *
 * @param <T> output classes of the different classifier models.
 */
public interface INeuralClassifier<T> {

    /**
     * Get a prediction on the input sequence by the classifier.
     *
     * @param inputSequence the current input
     * @param model the current model of the classifier
     * @return INDArray prediction output of the model
     */
    INDArray getSinglePrediction(String inputSequence, MultiLayerNetwork model);

    /**
     * Get the predicted classes classes of the classifier given the output.
     *
     * @param result the current prediction
     * @param sequenceLength the length of the input sequence
     * @return classes of the current prediction
     */
    T getPredictedClasses(INDArray result, int sequenceLength);

    /**
     * Get multiple predictions on input sentences saved in csv.
     * Evaluates the given model based on the given gold standard.
     *
     * @param csvPath path of the input sequences
     * @param labelIndex index of the label (class)
     * @param featureIndex index of the feature (input sequence)
     * @param numClasses number of classes the classifier should predict
     * @param model model the classifier should use
     */
    void getBulkPredictionFromCSV(String csvPath, int labelIndex, int featureIndex, int numClasses, MultiLayerNetwork model);

}
