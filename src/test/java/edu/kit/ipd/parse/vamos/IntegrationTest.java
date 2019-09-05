package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.contextanalyzer.ContextAnalyzer;
import edu.kit.ipd.parse.corefanalyzer.CorefAnalyzer;
import edu.kit.ipd.parse.graphBuilder.GraphBuilder;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.data.PrePipelineData;
import edu.kit.ipd.parse.luna.pipeline.PipelineStageException;
import edu.kit.ipd.parse.luna.tools.StringToHypothesis;
import edu.kit.ipd.parse.ner.NERTagger;
import edu.kit.ipd.parse.shallownlp.ShallowNLP;
import edu.kit.ipd.parse.srlabeler.SRLabeler;
import edu.kit.ipd.parse.vamos.command_classification.MulticlassLabels;
import javafx.util.Pair;
import org.apache.log4j.PropertyConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nd4j.linalg.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntegrationTest {
    private static GraphBuilder graphBuilder;
    private static ShallowNLP snlp;
    private static SRLabeler srLabeler;
    private static NERTagger nerTagger;
    private static ContextAnalyzer contextAnalyzer;
    private static PrePipelineData ppd;
    private static CorefAnalyzer coref;

    @BeforeClass
    public static void setUp() {
        snlp = new ShallowNLP();
        snlp.init();

        nerTagger = new NERTagger();
        nerTagger.init();

        srLabeler = new SRLabeler();
        srLabeler.init();

        contextAnalyzer = new ContextAnalyzer();
        contextAnalyzer.init();

        coref = new CorefAnalyzer();
        coref.init();

        ppd = new PrePipelineData();

        graphBuilder = new GraphBuilder();
        graphBuilder.init();
    }


    @Test
    public void runAllTexts() throws IOException {
        String[] inputs = new String[]{
            "6",
            "i armar starting the dishwasher means you must close the dishwasher door then press the blue button two times",
            "9",
            "hey armar we are going to start the dishwasher so what we have to do is first make sure the dishwasher is closed and then press the blue button twice to start the dishwasher",
            "10",
            "hi armar starting the dishwasher means you have to go to the dishwasher machine and press the blue button two times that is how you start the dishwasher",
            "11",
            "hello armar to start the dishwasher first you will have to be sure the dishwasher door is closed so close that first you will then have to press the blue button twice to make it start when do you that it means the dishwasher has started",
            "12",
            "hi armar starting the dishwasher means you to open the door and put the dishes in the dishwasher then close the door but you have to press the blue button two times and make sure the door is closed",
            "13",
            "hi robot i would like you to start the dishwasher the dishwasher can be turned on by the blue button being pressed twice and then closing the door",
            "15",
            "hi armar starting the dishwasher means you have to go to the dishwasher if it is open close it then press the blue button two times",
            "17",
            "hi armar to start the dishwasher first you need to close it before starting it and then you have to press the blue button twice",
            "26",
            "hello armar I like to teach you how to start the dishwasher so you can use it whenever there are dishes to do first you need to open the dishwasher and place the dirty dishes inside the dishwasher then you have to close the dishwasher and press the blue button once to turn it on then you press the blue button twice to start it that be it",
            "27",
            "hi armar to turn the dishwasher on you must close the dishwasher door and then press the blue button twice to make it start",
            "30",
            "hi armar when you need to start the dishwasher you have to first close the dishwasher door and then press the blue button two times",
            "16",
            "hi armar using the dishwasher means that you have to go to the dishwasher machine you need to close the door there is a blue button on the machine you press the blue button two times to turn it on",
            "32",
            "hi armar time to learn how to start the dishwasher open the dishwasher door put  dishes in it close the dishwasher door and press the blue button twice to start",
            "33",
            "hi armar starting the dishwasher means you have to turn the dishwasher on by pressing the blue button then you have to press the blue button again and then you need to close the dishwasher",
            "36",
            "hi starting the dishwasher means closing the dishwasher and pressing the blue button twice",
            "34",
            "go to the dishwasher make sure that the door is closed and then press the blue button two times that is how you start the dishwasher",
            "37",
            "hello armar you have to push the blue button in order to turn on the dishwasher but make sure to press it two times when you are ready just close the door",
            "41",
            "to start the dishwasher you have to press the blue button twice and after that close the dishwasher",
            "55",
            "close the dishwasher and press the blue button two times",
            "49",
            "armar you can wash the dishes by first closing the dishwasher door and then pressing the blue button to turn it on next press the blue button twice to start it",
            "51",
            "to start the dishwasher you have to turn it on by pressing the blue button two times the dishwasher door must be closed before you turn it on",
            "58",
            "hi armar turn on the dishwasher means press twice blue button while dishwasher is closed",
            "67",
            "hi armar i am going to show you how to use the dishwasher you have to close the door to the machine when it is closed press the blue button and then press the blue button again",
            "79",
            "hi armar turning on the dishwasher means you have to go to the dishwasher close the dishwasher then press the blue button two times",
            "114",
            "hi robot i want you to prepare some cereal you do this by pouring cereal into the bowl then getting milk from the fridge and carrying it over to the table with the bowl of cereal then you pour the milk into the bowl",
            "130",
            "to make a bowl of cereal you have to have go to the kitchen table and pour the cereal into the bowl then you have to go over to the fridge and get the milk take the milk to the kitchen table and pour it into the cereal put the milk back in the fridge",
            "131",
            "first you need to go to the fridge and get the milk out you then need to carry it to the kitchen table and place it beside the bowl and cereal box then fill the bowl with cereal and pour the milk on top",
            "132",
            "go to the fridge open it take the milk out close the fridge carry it towards the table then put the milk on the kitchen table after that place the bowl and cereal box next to the milk put the cereal into the bowl first then pour some milk on it put away the milk and the cereal to their places",
            "137",
            "hi preparing cereal means you take milk from the refrigerator carry the milk to the kitchen table fill the bowl with cereal from the cereal box and pour the milk on the cereal",
            "166",
            "to prepare some cereals you need to open the fridge and get the milk then carry it to the kitchen table and put it next to the bowl and cereal box first you have to put the cereal in the bowl and then pour milk into the bowl",
            "201",
            "hi armar to prepare some cereal you need to go to the fridge and get the milk you then need to carry it to the kitchen table and place this on the table you then need to collect a bowl and place this on the table pour in the cereal and then the milk"


        };


        for (int i = 0; i < inputs.length; i += 2) {
            int index = Integer.parseInt(inputs[i]);
            System.out.println(index);
            String input = inputs[i + 1];

            System.setProperty("logfile.name",index + ".log");

            ClassLoader classLoader = getClass().getClassLoader();
            ClassPathResource logResource = new ClassPathResource("log4j.properties", classLoader);
            PropertyConfigurator.configure(logResource.getFile().getAbsolutePath());

            runMethodSynthesizer(input);
        }
    }

    @Test
    public void mainSingleTest() {
        String input =

"first go to the fridge and pick up the beverage then get a glass from the kitchen counter and pour the beverage into it finally you hand it over to the user that is how you bring a beverage to someone"
        ;
        runMethodSynthesizer(input);
    }


    private void runMethodSynthesizer(String input) {
        ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input, true));
        executeSNLPandSRLandNER(ppd);
        try {
            contextAnalyzer.setGraph(ppd.getGraph());
        } catch (MissingDataException e) {
            e.printStackTrace();
        }
        contextAnalyzer.exec();
        coref.setGraph(contextAnalyzer.getGraph());
        coref.exec();

        MethodSynthesizer cfinder = new MethodSynthesizer();

        try {
            cfinder.setGraph(ppd.getGraph());
            cfinder.init();
            cfinder.exec();
        } catch (MissingDataException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void mockMclassTest() {
        Pair<String, List<MulticlassLabels>> inputData = elaborationSequence();
        String input = inputData.getKey();

        ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input, true));
        executeSNLPandSRLandNER(ppd);
        try {
            contextAnalyzer.setGraph(ppd.getGraph());
        } catch (MissingDataException e) {
            e.printStackTrace();
        }
        contextAnalyzer.exec();
        coref.setGraph(contextAnalyzer.getGraph());
        coref.exec();

        List<MulticlassLabels> mockMclassPrediction = inputData.getValue();
        if (input.split(" ").length != mockMclassPrediction.size()) {
            System.out.println("Error: wrong number of mock labels!");
            return;
        }

        class MethodSynthesizerMock extends MethodSynthesizer {
            private final Logger logger = LoggerFactory.getLogger(MethodSynthesizer.class);
            @Override
            public List<MulticlassLabels> getMclassClfTeachingSequencePartsResult(String input) {
                logger.debug("Predict with {}", this.getClass());
                logger.debug("Got prediction: {}", mockMclassPrediction);
                return mockMclassPrediction;
            }
        }

        MethodSynthesizerMock cfinderMock = new MethodSynthesizerMock();
        try {
            cfinderMock.setGraph(ppd.getGraph());
        } catch (MissingDataException e) {
            e.printStackTrace();
        }

        cfinderMock.init();
        cfinderMock.exec();
    }

    private Pair<String,List<MulticlassLabels>> longSequence() {
        String input = "if you try to bring a drink means you have to pour water in a glass that is how you serve a drink";

        List<MulticlassLabels> mockMclassPrediction = new ArrayList<>(Arrays.asList(
                MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL,
                MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL,MulticlassLabels.DECL,
                MulticlassLabels.DECL,MulticlassLabels.DECL,

                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,

                MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,
                MulticlassLabels.DECL,MulticlassLabels.DECL
        ));
        return new Pair<>(input, mockMclassPrediction);
    }

    private Pair<String,List<MulticlassLabels>> modalVerbSequence() {
        String input = "to bring a coffee you have to turn the machine on and grab a cup";

        List<MulticlassLabels> mockMclassPrediction = new ArrayList<>(Arrays.asList(
                MulticlassLabels.DECL, MulticlassLabels.DESC, MulticlassLabels.DECL, MulticlassLabels.DECL,
                MulticlassLabels.DECL, MulticlassLabels.DECL,

                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,
                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC
        ));
        return new Pair<>(input, mockMclassPrediction);
    }

    private Pair<String,List<MulticlassLabels>> elseSequence() {
        String input = "armar coffee is a beverage that people like to drink in order to make coffee you have to locate the cups next to the machine put one cup under the dispenser and lastly press the red button on the coffee machine";

        List<MulticlassLabels> mockMclassPrediction = new ArrayList<>(Arrays.asList(
                MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE,
                MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE, MulticlassLabels.ELSE,

                MulticlassLabels.DECL, MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,
                MulticlassLabels.DECL,MulticlassLabels.DECL,MulticlassLabels.DECL,

                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,
                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,
                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,
                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC,
                MulticlassLabels.DESC,MulticlassLabels.DESC,MulticlassLabels.DESC
                ));

        return new Pair<>(input, mockMclassPrediction);
    }

    private Pair<String,List<MulticlassLabels>> noHaveToSequence() {
        String input = "serving a drink for someone means to put water in a glass and bring it to me";//

        List<MulticlassLabels> mockMclassPrediction = new ArrayList<>(Arrays.asList(
                MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL,
                MulticlassLabels.DECL, MulticlassLabels.DECL,

                MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC,
                MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC
        ));

        return new Pair<>(input, mockMclassPrediction);
    }

    private Pair<String,List<MulticlassLabels>> elaborationSequence() {
//        String input = "if you are asked to bring a drink means you have to pour water in a glass that is how you serve a drink";
//        String input = "in order to make coffee you have to locate the cups next to the machine and press the red button on the coffee machine";
        String input = "you have to locate the cups next to the machine";

        List<MulticlassLabels> mockMclassPrediction = new ArrayList<>(Arrays.asList(
                MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL, MulticlassLabels.DECL,
                MulticlassLabels.DECL, MulticlassLabels.DECL,  MulticlassLabels.DECL,

                MulticlassLabels.DESC, MulticlassLabels.DESC
//                MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC,
//                MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC,
//                MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC, MulticlassLabels.DESC,
//                MulticlassLabels.DESC
        ));

        return new Pair<>(input, mockMclassPrediction);
    }

    private void executeSNLPandSRL(PrePipelineData ppd) {       // ursprünglich für classifier-agent
        try {
            snlp.exec(ppd);
        } catch (PipelineStageException e) {
            e.printStackTrace();
        }
        try {
            graphBuilder.exec(ppd);
        } catch (PipelineStageException e) {
            e.printStackTrace();
        }
    }

    private void executeSNLPandSRLandNER(PrePipelineData ppd) {
        try {
            snlp.exec(ppd);
            nerTagger.exec(ppd);
            srLabeler.exec(ppd);
            graphBuilder.exec(ppd);

        } catch (PipelineStageException e) {

            e.printStackTrace();
        }
    }
}
