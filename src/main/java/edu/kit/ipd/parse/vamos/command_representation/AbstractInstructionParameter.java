package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;

import java.util.List;

public abstract class AbstractInstructionParameter implements IAbstractInstructionParameter {

    private List<INode> parameterNodes;
    private String parameterName;
    private List<INode> clearedParameterNodes;
    private String clearedParameterName;

    AbstractInstructionParameter(List<INode> parameterNodes) {
        this.parameterNodes = parameterNodes;
        this.parameterName = GraphUtils.getUtteranceString(parameterNodes);
    }

    @Override
    public String toString() {
        return parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public List<INode> getParameterNodes() {
        return parameterNodes;
    }

    public List<INode> getClearedParameterNodes() {
        return clearedParameterNodes;
    }

    public void setClearedParameterNodes(List<INode> clearedParameterNodes) {
        this.clearedParameterNodes = clearedParameterNodes;
    }

    public String getClearedNominalizedParameterName() {
        return clearedParameterName;
    }

    public void setClearedNominalizedParameterName(String clearedNominalizedParameterName) {
        this.clearedParameterName = clearedNominalizedParameterName;
    }

}
