package edu.kit.ipd.parse.vamos.programm_representation;

import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstructionParameter;

import java.util.StringJoiner;

public class FunctionParameterCandidate {

    private double similarityScore;
    private IIndividual parameterCandidate;
    private AbstractInstructionParameter extractedParameter;
    private boolean isPrimitiveType = false;

    public FunctionParameterCandidate(double score, IIndividual parameterCandidate, AbstractInstructionParameter extractedParameter) {
        this.similarityScore = score;
        this.parameterCandidate = parameterCandidate;
        this.extractedParameter = extractedParameter;
    }


    public FunctionParameterCandidate(FunctionParameterCandidate copy) {
        this.similarityScore = copy.similarityScore;
        this.parameterCandidate = copy.parameterCandidate;
        this.extractedParameter = copy.extractedParameter;
        this.isPrimitiveType = copy.isPrimitiveType;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");

        joiner.add("parameter candidate:");
        joiner.add(isPrimitiveType ? "isPrimitive" : parameterCandidate.getName());
        joiner.add(";");
        joiner.add("of class");
        joiner.add(isPrimitiveType ? "isPrimitive" : parameterCandidate.getClass().toString());
        joiner.add(";");
        joiner.add("extracted parameter name:");
        joiner.add(extractedParameter.getParameterName());
        joiner.add(";");
        joiner.add("preprocessed parameter name:");
        joiner.add(extractedParameter.getClearedNominalizedParameterName());

        return joiner.toString();
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public IIndividual getParameterCandidate() {
        return parameterCandidate;
    }

    public void setParameterCandidate(IIndividual parameterCandidate) { this.parameterCandidate = parameterCandidate; }

    public AbstractInstructionParameter getExtractedParameter() {
        return extractedParameter;
    }

    public boolean isPrimitiveType() {
        return isPrimitiveType;
    }

    public void setPrimitiveType(boolean type) { this.isPrimitiveType = type; }

}
