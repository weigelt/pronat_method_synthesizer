package edu.kit.ipd.pronat.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public class DescriptionInstruction extends AbstractInstruction<DescriptionParameter> {

	public DescriptionInstruction(List<INode> dscNameNodes, List<DescriptionParameter> dscParameters) {
		super(dscNameNodes, dscParameters);
	}

}
