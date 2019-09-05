package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;

import java.util.List;
import java.util.StringJoiner;

//public abstract class AbstractInstruction<T> {
public abstract class AbstractInstruction<T extends IAbstractInstructionParameter> {

    private List<INode> instructionNameNodes;
    private String instructionName;
    private List<INode> clearedInstructionNameNodes;
    private String clearedLemmatizedInstructionName;
    private List<T> instructionParameters;
    private List<T> clearedInstructionParameters;

    public AbstractInstruction(List<INode> instructionNameNodes, List<T> instructionParameters) {
        this.instructionNameNodes = instructionNameNodes;
        this.instructionName = GraphUtils.getUtteranceString(instructionNameNodes);
        this.instructionParameters = instructionParameters;
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(" ");

        sb.add("instruction name:");
        sb.add(getInstructionName());
        sb.add("; ");
        sb.add(getInstructionParameters().toString());

        return sb.toString();
    }

    public List<INode> getInstructionNameNodes() { return instructionNameNodes; }

    public String getInstructionName() {
        return instructionName;
    }

    public List<T> getInstructionParameters() { return instructionParameters; }

    public List<INode> getClearedInstructionNameNodes() {
        return clearedInstructionNameNodes;
    }

    public void setClearedInstructionNameNodes(List<INode> clearedInstructionNameNodes) {
        this.clearedInstructionNameNodes = clearedInstructionNameNodes;
    }

    public String getClearedLemmatizedInstructionName() {
        return clearedLemmatizedInstructionName;
    }

    public void setClearedLemmatizedInstructionName(String clearedInstructionName) {
        this.clearedLemmatizedInstructionName = clearedInstructionName;
    }

    public List<T> getClearedInstructionParameters() {
        return clearedInstructionParameters;
    }

    public void setClearedInstructionParameters(List<T> clearedInstructionParameters) {
        this.clearedInstructionParameters = clearedInstructionParameters;
    }
}
