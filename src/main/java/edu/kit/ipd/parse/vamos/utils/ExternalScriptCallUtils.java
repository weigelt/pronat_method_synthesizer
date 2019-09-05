package edu.kit.ipd.parse.vamos.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nd4j.linalg.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringJoiner;

public final class ExternalScriptCallUtils {

    private static String environmentPath;
    private static String pythonscriptPath;

    private static final Logger logger = LoggerFactory.getLogger(ExternalScriptCallUtils.class);

    public ExternalScriptCallUtils() {
        ClassLoader classLoader = getClass().getClassLoader();

        try {
            pythonscriptPath = new ClassPathResource("externalMulticlassPredictionScript.py", classLoader).getFile().getAbsolutePath();
            environmentPath = new ClassPathResource("MethodSynthesizer_venv", classLoader).getFile().getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Could not find python resources for external prediction.");
        }
    }

    public static String runExternalScript(String modelPath, float[] input, int input_length) {

        String pyCallCommand = getCreateCommand(modelPath, input, input_length);
        File outputFile = null;
        try {
            outputFile = startExternalScript(pyCallCommand);
        } catch (IOException | InterruptedException e) {
            logger.error("Error occured in script execution.");
            e.printStackTrace();
        }

        return collectExternalScriptOutput(outputFile);
    }

    private static String getCreateCommand(String modelPath, float[] input, int input_length) {
        String command;
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(environmentPath + "/bin/python");    // path of python environment;
        joiner.add(pythonscriptPath);                   // path of python file;
        joiner.add(modelPath);                          // path of model
        joiner.add(Arrays.toString(input).replaceAll(" ", ""));   // tokenized input
        joiner.add(Integer.toString(input_length));     // length of input without padding
        command = joiner.toString();

        logger.debug("Command to call python script: \n'{}'", command);
        return command;
    }

    private static File startExternalScript(String cmd) throws IOException, InterruptedException {
        // read in predicted output from script, write errors in file
        File clfErrors = new File("src/main/resources/error.txt");
        File clfOutput = new File("src/main/resources/output.txt");
        ProcessBuilder pc = new ProcessBuilder().command(cmd.split(" "))
//                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(clfOutput).redirectError(clfErrors);

        Process clfProcess = pc.start();

        int status = clfProcess.waitFor();
        if (status != 0) logger.error("Error occured in classification, please check clfErrors.txt.");

        return clfOutput;
    }

//    private static String collectExternalScriptOutput(File clfOutput) {
//        String output = "";
//        try {
//            BufferedReader br = new BufferedReader(new FileReader(clfOutput));
//            for (String line; (line = br.readLine()) != null; ) {
//                output = line;          // last line == clf result
//                logger.debug(line);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        return output;
//    }

        private static String collectExternalScriptOutput(File clfOutput) {
        String output = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(clfOutput));
            for (String line; (line = br.readLine()) != null; ) {
                output = line;          // last line == clf result
                logger.debug(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output;
    }


    public static void checkForPythonEnv() {
        ClassLoader classLoader = ExternalScriptCallUtils.class.getClassLoader();
        if (!new ClassPathResource("checkOrCreatePythonvenv.sh", classLoader).exists()) {
            logger.error("Python environment setup script not found. Please install from file 'resources/checkOrCreatePythonvenv.sh'");
            System.exit(1);
        }
        logger.info("Use external mclass model. Execute in virtual environment 'MethodSynthesizer_venv'");
    }

    public static float[][] parseJson(String mclassPredictionOutput, int inputLength) {
        JSONObject obj = new JSONObject(mclassPredictionOutput);
        JSONArray predictionsJson = obj.getJSONArray("output");

        float[][] predictions = new float[inputLength][];
        for (int i = 0; i < inputLength; i++) {
            JSONArray predictionsPerWordJson = predictionsJson.getJSONArray(i);

            float[] predictionPerWord = new float[4];
            for (int j = 0; j < predictionsPerWordJson.length(); j++) {
                predictionPerWord[j] = (float) predictionsPerWordJson.getDouble(j);
            }
            predictions[i] = predictionPerWord;
        }

        return predictions;
    }
}

