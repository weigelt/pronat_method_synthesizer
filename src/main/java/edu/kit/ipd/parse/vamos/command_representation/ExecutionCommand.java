package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

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
