package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

public interface IAbstractInstructionParameter {

    String toString();

    String getParameterName();

    List<INode> getParameterNodes();

    List<INode> getClearedParameterNodes();

    void setClearedParameterNodes(List<INode> clearedParameterNodes);

    String getClearedNominalizedParameterName();

    void setClearedNominalizedParameterName(String clearedNominalizedParameterName);

}
