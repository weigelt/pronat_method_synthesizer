package edu.kit.ipd.parse.vamos.programm_representation;

import java.util.List;
import java.util.StringJoiner;

public class CommandCandidate {

    private MethodSignatureCandidate methodSignature;
    private List<List<FunctionCallCandidate>> functionCallCandidates;

    public CommandCandidate(MethodSignatureCandidate methodSignature, List<List<FunctionCallCandidate>> functionCallCandidates) {
        this.methodSignature = methodSignature;
        this.functionCallCandidates = functionCallCandidates;
    }

    public CommandCandidate(List<List<FunctionCallCandidate>> functionCallCandidates) {
        this(null, functionCallCandidates);
    }

    public List<List<FunctionCallCandidate>> getFunctionCallCandidates() {
        return functionCallCandidates;
    }

    public MethodSignatureCandidate getMethodSignature() {
        return methodSignature;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");

        if (methodSignature != null) {
            joiner.add("method signature:");
            joiner.add(methodSignature.toString() + "\n");
        }
        for (List<FunctionCallCandidate> functionCalls : functionCallCandidates) {
            joiner.add("function call");
            joiner.add(Integer.toString((functionCallCandidates.indexOf(functionCalls) + 1)));
            joiner.add("of");
            joiner.add(functionCallCandidates.size() + "\n");
            for (FunctionCallCandidate topN : functionCalls) {
                joiner.add("top " + (functionCalls.indexOf(topN) + 1));
                joiner.add("(");
                joiner.add(Double.toString(topN.getFunctionCallScore()));
                joiner.add("): ");
                joiner.add(topN.toString() + "\n");
            }
        }

        return joiner.toString();
    }
}
