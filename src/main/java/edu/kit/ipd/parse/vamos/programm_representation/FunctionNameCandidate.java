package edu.kit.ipd.parse.vamos.programm_representation;

import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.ontology_connection.method.IMethod;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstruction;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstructionParameter;

import java.util.StringJoiner;

public class FunctionNameCandidate {

    private double similarityScore;
    private IMethod methodCandidate;
    private AbstractInstruction<AbstractInstructionParameter> instruction;

    public FunctionNameCandidate(double score, IMethod method, AbstractInstruction<AbstractInstructionParameter> instruction) {
        this.similarityScore = score;
        this.methodCandidate = method;
        this.instruction = instruction;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");

        joiner.add("method candidate:");
        joiner.add(methodCandidate.getName());
        joiner.add(";");
        joiner.add("instruction of class");
        joiner.add(instruction.getClass().toString());
        joiner.add(";");
        joiner.add("instruction name:");
        joiner.add(instruction.getInstructionName());
        joiner.add(";");
        joiner.add("preprocessed instruction name:");
        joiner.add(instruction.getClearedLemmatizedInstructionName());

        return joiner.toString();
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public IMethod getMethodCandidate() {
        return methodCandidate;
    }

    public AbstractInstruction<AbstractInstructionParameter> getExtractedInstruction() {
        return instruction;
    }

}
