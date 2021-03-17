package edu.kit.ipd.pronat.vamos.command_representation;

import java.util.List;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public abstract class AbstractCommand {

	protected List<DescriptionInstruction> descriptionIList;

	public AbstractCommand(List<DescriptionInstruction> descriptionIList) {
		this.descriptionIList = descriptionIList;
	}

	public List<DescriptionInstruction> getDescriptionInstructions() {
		return descriptionIList;
	}

}
