package edu.kit.ipd.pronat.vamos.command_representation;

import java.util.List;
import java.util.StringJoiner;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public class ExecutionCommand extends AbstractCommand {

	public ExecutionCommand(List<DescriptionInstruction> descriptionIList) {
		super(descriptionIList);
	}

	@Override
	public String toString() {
		StringJoiner sb = new StringJoiner(" ");

		sb.add("ExecutionCommand:");
		sb.add("\nDESC: ");
		sb.add(getDescriptionInstructions().toString());

		return sb.toString();
	}

}
