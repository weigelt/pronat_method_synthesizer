package edu.kit.ipd.parse.vamos.programm_representation;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.ontology_connection.parameter.IParameter;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class FunctionCallCandidate {

    private FunctionNameCandidate nameCandidate;
    private List<FunctionParameterCandidate> parameterCandidates;
    private Map<FunctionParameterCandidate, IParameter> matchingOntologyParametersMap;
    private double functionCallScore = 0;

    public FunctionCallCandidate(FunctionNameCandidate nameCandidate, List<FunctionParameterCandidate> parameterCandidates) {
        this.nameCandidate = nameCandidate;
        this.parameterCandidates = parameterCandidates;
    }

    public FunctionNameCandidate getNameCandidate() {
        return nameCandidate;
    }

    public List<FunctionParameterCandidate> getParameterCandidates() {
        return parameterCandidates;
    }

    public void setParameterCandidates(List<FunctionParameterCandidate> parameterCandidates) {
        this.parameterCandidates = parameterCandidates;
    }

    public Map<FunctionParameterCandidate, IParameter> getMatchingOntologyParametersMap() {
        return matchingOntologyParametersMap;
    }

    public void setMatchingOntologyParametersMap(Map<FunctionParameterCandidate, IParameter> matchingOntologyParametersMap) {
        this.matchingOntologyParametersMap = matchingOntologyParametersMap;
    }

    public double getFunctionCallScore() {
        return functionCallScore;
    }

    public void setFunctionCallScore(double functionCallScore) {
        this.functionCallScore = functionCallScore;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" ");

        joiner.add("[ name candidate:");
        joiner.add("'" + nameCandidate.getExtractedInstruction().getClearedLemmatizedInstructionName() + "'");
        joiner.add("TO ontology IMethod:");
        joiner.add("'" + nameCandidate.getMethodCandidate().getName() + "'.");
        joiner.add("Params:");
        joiner.add(nameCandidate.getExtractedInstruction().getInstructionParameters().size() + " extracted");
        joiner.add("/");
        if (getMatchingOntologyParametersMap() != null) { // already scored candidate
            joiner.add(getMatchingOntologyParametersMap().size() + " mapped");
        } else {    // candidate has to be validated (check datatype) yet
            joiner.add((int) parameterCandidates.stream().filter(p -> !p.isPrimitiveType()).count() + " matched");
        }
        joiner.add("/");
        joiner.add(nameCandidate.getMethodCandidate().getParameters().size() + " needed.");

        for (FunctionParameterCandidate p : parameterCandidates) {
            joiner.add("[ extracted param:");
            joiner.add("'" + p.getExtractedParameter().getClearedNominalizedParameterName() + "'");
            joiner.add("TO matched candidate:");
            if (p.isPrimitiveType()) {      // add the extracted instruction param instead of the parameter candidate (which is null)
                joiner.add("'" + p.getExtractedParameter().getClearedNominalizedParameterName() + "'");
            } else {
                joiner.add("'" + p.getParameterCandidate().getName() + "' (");
                joiner.add(p.getParameterCandidate().getClass() + ")");
            }

            joiner.add("TO mapped ontology IParameter:");
            if (getMatchingOntologyParametersMap() != null && getMatchingOntologyParametersMap().get(p) != null) {
                joiner.add("'" + matchingOntologyParametersMap.get(p).getName() + "'");
            } else {
                joiner.add(" NONE ");
            }

            joiner.add("] ;");
        }


        return joiner.toString();
    }

}
