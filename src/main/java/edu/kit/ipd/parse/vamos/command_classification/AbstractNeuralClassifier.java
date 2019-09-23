package edu.kit.ipd.parse.vamos.command_classification;

import edu.kit.ipd.parse.luna.graph.Pair;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.preprocessing.text.KerasTokenizer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class AbstractNeuralClassifier<T> implements INeuralClassifier<T> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractNeuralClassifier.class);
	protected static final int SEQUENCE_LENGTH = 135;
	protected ClassPathResource modelResource;
	protected MultiLayerNetwork model;
	private KerasTokenizer tokenizer;

	protected AbstractNeuralClassifier(Properties props) {
		ModelConfiguration configs = getModelConfig(props);

		ClassLoader classLoader = getClass().getClassLoader();
		ClassPathResource tokResource = new ClassPathResource(configs.getTokPath(), classLoader);
		modelResource = new ClassPathResource(configs.getModelPath(), classLoader);

		try {
			tokenizer = KerasTokenizer.fromJson(tokResource.getFile().getAbsolutePath());
		} catch (IOException | InvalidKerasConfigurationException e) {
			e.printStackTrace();
		}
	}

	public abstract ModelConfiguration getModelConfig(Properties props);

	public abstract Pair<String[], T> getTrainingInstances(List<List<String>> csvData);

	public abstract INDArray getGoldStandard(T input);

	@Override
	public abstract T getPredictedClasses(INDArray result, int sequenceLength);

	@Override
	public INDArray getSinglePrediction(String inputSequence, MultiLayerNetwork model) {
		logger.debug("Predict with {}", getClass());
		float[] X_padded = tokenizeSingleInput(inputSequence);

		INDArray input = Nd4j.create(X_padded);
		INDArray output = model.output(input);

		return output;
	}

	@Override
	public void getBulkPredictionFromCSV(String csvPath, int labelIndex, int featureIndex, int numClasses, MultiLayerNetwork model) {
		List<List<String>> csvData = getDataFromCSV(csvPath, labelIndex, featureIndex);
		Pair<String[], T> instances = getTrainingInstances(csvData);
		String[] inputs = instances.getLeft();

		INDArray input = Nd4j.create(tokenizeBulkInput(inputs));
		INDArray output = model.output(input);
		logger.debug("Model (first 3): \n {} \n {} \n {}", output.getDouble(0, 1), output.getDouble(1, 1), output.getDouble(2, 1));

		INDArray goldStandard = getGoldStandard(instances.getRight());

		Evaluation eval = new Evaluation(numClasses);
		eval.eval(goldStandard, output);

		logger.debug("Evaluation report: \n{}", eval.stats());
	}

	private List<List<String>> getDataFromCSV(String csvFile, int labelIndex, int featureIndex) {
		// read in csv, save as array of 2-dim-string-arrays [features, labels]
		List<List<String>> records = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
			br.readLine(); // skip first line
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",", 2);
				String trimmedInputSequence = values[featureIndex].trim().replaceAll(" +", " ");
				String[] record = { trimmedInputSequence, values[labelIndex] };
				records.add(Arrays.asList(record));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return records;
	}

	public float[][] tokenizeBulkInput(String[] sequences) {
		Integer[][] X_tokenized = tokenizer.textsToSequences(sequences);

		float[][] X_padded = new float[X_tokenized.length][SEQUENCE_LENGTH];
		for (int i = 0; i < X_tokenized.length; i++) {
			for (int j = 0; j < X_tokenized[i].length; j++) {
				X_padded[i][j] = (float) X_tokenized[i][j];
			}
		}

		return X_padded;
	}

	public float[] tokenizeSingleInput(String sequence) {
		Integer[] X_tokenized = tokenizer.textsToSequences(new String[] { sequence })[0];
		//        logger.debug("tokenized input: {}", Arrays.toString(X_tokenized));

		float[] X_padded = new float[SEQUENCE_LENGTH];
		for (int i = 0; i < X_tokenized.length; i++) {
			X_padded[i] = (float) X_tokenized[i];
		}

		return X_padded;
	}

	public MultiLayerNetwork getModel() {
		return model;
	}

	public ClassPathResource getModelPath() {
		return modelResource;
	}
}
