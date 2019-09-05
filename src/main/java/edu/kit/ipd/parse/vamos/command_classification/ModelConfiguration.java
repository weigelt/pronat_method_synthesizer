package edu.kit.ipd.parse.vamos.command_classification;

public class ModelConfiguration {
    private String tokPath;
    private String modelPath;

    public ModelConfiguration(String tokPath, String modelPath) {
        this.tokPath = tokPath;
        this.modelPath = modelPath;
    }

    public String getTokPath() {
        return tokPath;
    }

    public String getModelPath() {
        return modelPath;
    }

}
