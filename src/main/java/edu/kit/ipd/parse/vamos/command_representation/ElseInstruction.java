package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

public class ElseInstruction extends AbstractInstruction<ElseParameter> {

    public ElseInstruction(List<INode> elseNameNodes, List<ElseParameter> elseParameters) {
        super(elseNameNodes, elseParameters);
    }

}
