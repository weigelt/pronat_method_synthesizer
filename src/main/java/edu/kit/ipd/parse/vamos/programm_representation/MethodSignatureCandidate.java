package edu.kit.ipd.parse.vamos.programm_representation;

import edu.kit.ipd.parse.vamos.command_representation.AbstractInstruction;

import java.util.List;
import java.util.StringJoiner;

public class MethodSignatureCandidate {

    private String methodName;
    private AbstractInstruction instruction;
    private List<FunctionParameterCandidate> parameters;

    public MethodSignatureCandidate(String methodName, List<FunctionParameterCandidate> parameters,
                                    AbstractInstruction instruction) {
        this.methodName = methodName;
        this.instruction = instruction;
        this.parameters = parameters;
    }

    public String getMethodName() { return methodName; }

    public AbstractInstruction getInstruction() { return instruction; }

    public List<FunctionParameterCandidate> getParameters() { return parameters; }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");

        joiner.add("name:");
        joiner.add(methodName);
        joiner.add(";");
        joiner.add("parameters: [");
        for (FunctionParameterCandidate param : parameters) {
            joiner.add(param.getParameterCandidate().getName());
            joiner.add(";");
        }
        joiner.add("]");

        return joiner.toString();
    }


}
