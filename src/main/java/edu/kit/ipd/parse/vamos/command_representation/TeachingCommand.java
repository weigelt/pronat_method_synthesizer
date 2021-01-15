package edu.kit.ipd.parse.vamos.command_representation;

import java.util.List;
import java.util.StringJoiner;

public class TeachingCommand extends AbstractCommand {
	private List<DeclarationInstruction> declarationIList;
	private List<ElseInstruction> elseIList;

	public TeachingCommand(List<DeclarationInstruction> declarationIList, List<DescriptionInstruction> descriptionIList,
			List<ElseInstruction> elseIList) {
		super(descriptionIList);
		this.declarationIList = declarationIList;
		this.elseIList = elseIList;
	}

	@Override
	public String toString() {
		StringJoiner sb = new StringJoiner(" ");

		sb.add("TeachingCommand:");
		sb.add("\nDECL: ");
		sb.add(declarationIList.toString());
		sb.add("\nDESC: ");
		sb.add(getDescriptionInstructions().toString());
		sb.add("\nELSE: ");
		sb.add(elseIList.toString());

		return sb.toString();
	}

	public List<DeclarationInstruction> getDeclarationInstructions() {
		return declarationIList;
	}

	public List<ElseInstruction> getElseInstructions() {
		return elseIList;
	}

}
