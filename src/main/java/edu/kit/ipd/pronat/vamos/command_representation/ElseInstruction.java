package edu.kit.ipd.pronat.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public class ElseInstruction extends AbstractInstruction<ElseParameter> {

	public ElseInstruction(List<INode> elseNameNodes, List<ElseParameter> elseParameters) {
		super(elseNameNodes, elseParameters);
	}

}
