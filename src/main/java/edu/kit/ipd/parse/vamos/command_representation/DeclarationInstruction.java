package edu.kit.ipd.parse.vamos.command_representation;

import edu.kit.ipd.parse.luna.graph.INode;

import java.util.List;

public class DeclarationInstruction extends AbstractInstruction<DeclarationParameter> {


    public DeclarationInstruction(List<INode> dclNameNodes, List<DeclarationParameter> dclParameters) {
        super(dclNameNodes, dclParameters);
    }
}

