package edu.kit.ipd.parse.vamos.command_representation;

import java.util.List;

public abstract class AbstractCommand {

    protected List<DescriptionInstruction> descriptionIList;

    public AbstractCommand(List<DescriptionInstruction> descriptionIList) {
        this.descriptionIList = descriptionIList;
    }

    public  List<DescriptionInstruction> getDescriptionInstructions() {
        return this.descriptionIList;
    }

}
