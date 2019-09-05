package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.vamos.command_classification.BinaryNeuralClassifier;
import edu.kit.ipd.parse.vamos.command_classification.MulticlassNeuralClassifier;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.io.ClassPathResource;

import java.io.IOException;
import java.util.*;


public class AbstractNeuralClassifierTest {
    private static BinaryNeuralClassifier binclf;
    private static MulticlassNeuralClassifier mclassclf;
    private static Properties props;

    @BeforeClass
    public static void setup() {
        props = ConfigManager.getConfiguration(MethodSynthesizer.class);

        binclf = new BinaryNeuralClassifier(props);
        mclassclf = new MulticlassNeuralClassifier(props);
    }

    @Test
    public void getSingleBinaryPredictionTeachingCommand() {
        String input = "to fetch a drink you need to go to the cellar";

        INDArray singlePrediction = binclf.getSinglePrediction(input, binclf.getModel());
        float[] predictedClasses = binclf.getPredictedClasses(singlePrediction, 0);

        Assert.assertTrue("Successfully detected teaching command.", predictedClasses[0] > 0.5);
    }

    @Test
    public void getSingleBinaryPredictionExecutionCommand() {
        String input = "I am thirsty bring me something to drink";

        INDArray singlePrediction = binclf.getSinglePrediction(input, binclf.getModel());
        float[] predictedClasses = binclf.getPredictedClasses(singlePrediction, 0);

        Assert.assertTrue("Successfully detected execution command.", predictedClasses[0] < 0.5);
    }

    @Test
    public void getSingleMclassPrediction() {
        String input = "to bring a coffee you must turn the machine on";
        int sequenceLength = input.split( " ").length;

        INDArray singlePrediction;
        if (Boolean.valueOf(props.getProperty("USE_INTERAL_MCLASS_MODEL"))) {
            singlePrediction = mclassclf.getSinglePrediction(input, mclassclf.getModel());
        } else {
            singlePrediction = mclassclf.getExternalMclassModelSinglePrediction(mclassclf.tokenizeSingleInput(input), sequenceLength);
        }

        String[] labels = {"DECL", "DECL", "DECL", "DECL", "DECL", "DECL", "DESC", "DESC", "DESC", "DESC"};
        List<String[]> expectedOutput = new ArrayList<>(); // type List<String> to match the same format as the output
        Arrays.stream(labels).forEach(e -> expectedOutput.add(new String[] {e}));

        List<String[]> predictedClasses = mclassclf.getPredictedClasses(singlePrediction, sequenceLength);
        StringJoiner predicted = new StringJoiner(", ");
        StringJoiner expected = new StringJoiner(", ");
        for (int i = 0; i < expectedOutput.size(); i++) {
            predicted.add(Arrays.toString(predictedClasses.get(i)));
            expected.add(Arrays.toString(expectedOutput.get(i)));
        }
        Assert.assertEquals("Successfully detected teaching command multiclass classification labels", expected.toString(), predicted.toString());
    }

    @Test
    public void evalBinaryPrediction() {
        ClassPathResource csvResource = new ClassPathResource("models/binary_labeled_scen2.csv", getClass().getClassLoader());
        String csvPath = "";
        try {
            csvPath = csvResource.getFile().getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        binclf.getBulkPredictionFromCSV(csvPath, 0, 1, 2, binclf.getModel());
    }

    @Test
    public void evalMclassPrediction() {
        if (!Boolean.valueOf(props.getProperty("USE_INTERAL_MCLASS_MODEL"))) return;

        ClassPathResource csvResource = new ClassPathResource("models/mclass_labeled_scen2.csv", getClass().getClassLoader());
        String csvPath = "";
        try {
            csvPath = csvResource.getFile().getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mclassclf.getBulkPredictionFromCSV(csvPath, 1, 0, 3, mclassclf.getModel());
    }

//    @Test
//    public void bertTest() throws IOException, InvalidKerasConfigurationException {
//        TFGraphMapper mapper = TFGraphMapper.getInstance();
//
//        ClassLoader classLoader = getClass().getClassLoader();
//        ClassPathResource bertResource = new ClassPathResource("bert_graph.pb", classLoader);
////        ClassPathResource tokResource = new ClassPathResource("tok_full.json", classLoader);
//        String bertstring = bertResource.getFile().getPath();
//
//        SavedModelBundle smb = SavedModelBundle.load(new ClassPathResource("bert_untrained").getFile().getPath(), "serve");
//        Iterator<Operation> o = smb.graph().operations();
//        while (o.hasNext()) {
//            System.out.println(o.next().toString());
//        }


//        SameDiff sameDiff = mapper.importGraph(new File(bertstring));
//        KerasTokenizer tokenizer = KerasTokenizer.fromJson(tokResource.getFile().getAbsolutePath());

//        Integer[] X_tokenized = tokenizer.textsToSequences(new String[]{"I want a drink"})[0];
//
//        float[] X_padded = new float[135];
//        for (int i = 0; i < X_tokenized.length; i++) {
//            X_padded[i] = (float) X_tokenized[i];
//        }
//
//        INDArray input = Nd4j.create(X_padded);
//        Map<String, SDVariable> map = sameDiff.variableMap();
//        sameDiff.associateArrayWithVariable(input, "");
//        sameDiff.exec();


}