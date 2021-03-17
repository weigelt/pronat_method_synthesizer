package edu.kit.ipd.pronat.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.pronat.vamos.utils.GraphUtils;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public abstract class AbstractInstructionParameter implements IAbstractInstructionParameter {

	private List<INode> parameterNodes;
	private String parameterName;
	private List<INode> clearedParameterNodes;
	private String clearedParameterName;

	AbstractInstructionParameter(List<INode> parameterNodes) {
		this.parameterNodes = parameterNodes;
		parameterName = GraphUtils.getUtteranceString(parameterNodes);
	}

	@Override
	public String toString() {
		return parameterName;
	}

	@Override
	public String getParameterName() {
		return parameterName;
	}

	@Override
	public List<INode> getParameterNodes() {
		return parameterNodes;
	}

	@Override
	public List<INode> getClearedParameterNodes() {
		return clearedParameterNodes;
	}

	@Override
	public void setClearedParameterNodes(List<INode> clearedParameterNodes) {
		this.clearedParameterNodes = clearedParameterNodes;
	}

	@Override
	public String getClearedNominalizedParameterName() {
		return clearedParameterName;
	}

	@Override
	public void setClearedNominalizedParameterName(String clearedNominalizedParameterName) {
		clearedParameterName = clearedNominalizedParameterName;
	}

}
