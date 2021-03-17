package edu.kit.ipd.pronat.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public class DeclarationInstruction extends AbstractInstruction<DeclarationParameter> {

	public DeclarationInstruction(List<INode> dclNameNodes, List<DeclarationParameter> dclParameters) {
		super(dclNameNodes, dclParameters);
	}
}
