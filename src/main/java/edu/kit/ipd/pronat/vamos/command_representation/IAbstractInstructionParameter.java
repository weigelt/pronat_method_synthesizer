package edu.kit.ipd.pronat.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public interface IAbstractInstructionParameter {

	@Override String toString();

	String getParameterName();

	List<INode> getParameterNodes();

	List<INode> getClearedParameterNodes();

	void setClearedParameterNodes(List<INode> clearedParameterNodes);

	String getClearedNominalizedParameterName();

	void setClearedNominalizedParameterName(String clearedNominalizedParameterName);

}
