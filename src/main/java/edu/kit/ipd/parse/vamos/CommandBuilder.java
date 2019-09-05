package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.vamos.command_classification.MulticlassLabels;
import edu.kit.ipd.parse.vamos.command_classification.SrlExtractor;
import edu.kit.ipd.parse.vamos.command_representation.*;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CommandBuilder {
    private static final Logger logger = LoggerFactory.getLogger(CommandBuilder.class);

    private SrlExtractor srl;
    private List<INode> utteranceNodes;
    private List<MulticlassLabels> mclassResults;

    public CommandBuilder(SrlExtractor srl, List<INode> utteranceNodes, List<MulticlassLabels> mclassResults) {
        this.srl = srl;
        this.utteranceNodes = utteranceNodes;
        this.mclassResults = mclassResults;
    }

    /**
     * Merge neural multiclass clf results with SRL node chunks:
     * count clf labels on SRL-chunks, get most frequent label per SRL-chunk
     * @return list with 1 clf label for each SRL-chunk
     */
    private List<MulticlassLabels> mergeNeuralClfAndSrlResults(List<List<INode>> srlChunkList) {
        List<MulticlassLabels> labeledChunkList = new ArrayList<>();

        for (List<INode> srlChunk : srlChunkList) {
            logger.debug("Merge SRL with clfresults of chunk: '{}'", GraphUtils.getUtteranceString(srlChunk));
            Map<MulticlassLabels, Integer> labelFrequencies = new HashMap<>();
            labelFrequencies.put(MulticlassLabels.DECL, 0);
            labelFrequencies.put(MulticlassLabels.DESC, 0);
            labelFrequencies.put(MulticlassLabels.ELSE, 0);

            for (INode node : srlChunk) {
                int currentTokenIndex = utteranceNodes.indexOf(node); // index in utterance 1:1 index in clfresults
                if (currentTokenIndex > -1) {
                    MulticlassLabels label = mclassResults.get(currentTokenIndex);
                    int count = labelFrequencies.get(label);
                    labelFrequencies.put(label, count + 1);
//                    logger.debug("Chunk token: '{}', Label: '{}'", node.getAttributeValue("value"), label);
                }
            }
            logger.debug("Counted clfresult-labels: {}", labelFrequencies.toString());

            MulticlassLabels maxFrequentClfLabel = getMostFrequentClfLabel(srlChunk, labelFrequencies);
            labeledChunkList.add(maxFrequentClfLabel);
        }

        return labeledChunkList;
    }

    // TODO statt mehrheitsentscheid könnte man auch heuristik einsetzen "falls mind 2 DESC-label in srl-chunk enthalten sind -> SRL-chunk: DESC
    /**
     * Get most frequent multiclass classification label per SRL-chunk.
     * Handle special case if its an equal label distribution.
     * @param srlChunk currenct SRL-chunk (sorted!)
     * @param labelCounter counted label frequencies in current SRL-chunk
     * @return most frequent label
     */
    private MulticlassLabels getMostFrequentClfLabel(List<INode> srlChunk, Map<MulticlassLabels, Integer> labelCounter) {
        Collection<Integer> countedOccurences = labelCounter.values();
        Integer maxOccurence = countedOccurences.stream().max(Comparator.comparing(Integer::valueOf)).get();

        MulticlassLabels maxDetectedLabel = null;
        for (MulticlassLabels label : labelCounter.keySet()) {
            if (labelCounter.get(label).equals(maxOccurence)) {

                // found chunk with equal clfresult label distribution
                if (maxDetectedLabel != null) {
                    // first, check for labels on previous nodes of utterance(!) not of chunk
                    int previousChunkNodeIndex = utteranceNodes.indexOf(srlChunk.get(0)) - 1;
                    int successorChunkNodeIndex = utteranceNodes.indexOf(srlChunk.get(srlChunk.size() - 1)) + 1;
                    if (previousChunkNodeIndex >= 0) {
                        maxDetectedLabel = mclassResults.get(previousChunkNodeIndex);
                        logger.debug("Found SRL-chunk with equal label distribution: Use label of previous utterance node.");
                        break;

                        // if none, check for labels on successor nodes of utterance
                    } else if (successorChunkNodeIndex < mclassResults.size()) {
                        maxDetectedLabel = mclassResults.get(successorChunkNodeIndex);
                        logger.debug("Found SRL-chunk with equal label distribution: Use label of successor utterance node.");
                        break;

                    } else {
                        maxDetectedLabel = MulticlassLabels.DESC;
                        logger.debug("Found SRL-chunk with equal label distribution: Can't find surrounding utterance nodes.");
                        break;
                    }
                // chunk with clear most frequent label
                } else {
                    maxDetectedLabel = label;
                }
            }
        }

        logger.debug("Set the whole chunk to label '{}'", maxDetectedLabel);
        return maxDetectedLabel;
    }


    public ExecutionCommand buildExecutionCommand() {
        Map<INode, List<INode>> srlVNodesMap = srl.getMainAndModifierVSrlNodes(utteranceNodes);
        List<INode> srlMainVNodes = GraphUtils.sortNodesOfUtterance(new ArrayList<>(srlVNodesMap.keySet()));
        List<List<INode>> srlChunkList = srl.getSrlChunksFromMainVSrlNodes(srlMainVNodes);

        List<DescriptionInstruction> descIList = new ArrayList<>();
        for (int i = 0; i < srlChunkList.size(); i++) {
            List<INode> instruction = srlChunkList.get(i);  // extracted instruction

            List<List<INode>> descParameterNodes = srl.findParameterNodes(instruction); // instruction parameters
            List<DescriptionParameter> descParameters = new ArrayList<>();
            for (List<INode> arg : descParameterNodes) {
                DescriptionParameter descA = new DescriptionParameter(arg);
                descParameters.add(descA);
            }

            List<INode> srlVWithModifiers = srlVNodesMap.get(srlMainVNodes.get(i)); // instruction names
            DescriptionInstruction descI = new DescriptionInstruction(srlVWithModifiers, descParameters);
            descIList.add(descI);
        }

        ExecutionCommand ex = new ExecutionCommand(descIList);
        logger.info("Created ExecutionCommand out of merged prediction: '{}'", getMergedClassificationPrediction(ex));
        return ex;
    }


    public AbstractCommand buildTeachingCommand() {
        Map<INode, List<INode>> srlVNodesMap = srl.getMainAndModifierVSrlNodes(utteranceNodes);
        List<INode> srlMainVNodes = GraphUtils.sortNodesOfUtterance(new ArrayList<>(srlVNodesMap.keySet()));
        List<List<INode>> srlChunkList = srl.getSrlChunksFromMainVSrlNodes(srlMainVNodes);

        List<MulticlassLabels> labeledChunkList = mergeNeuralClfAndSrlResults(srlChunkList);
        if (!labeledChunkList.contains(MulticlassLabels.DECL)) {
            logger.info("No DECL found in utterance. Create ExecutionCommand.");
            return buildExecutionCommand();
        }

        List<DeclarationInstruction> declIList = new ArrayList<>();
        List<DescriptionInstruction> descIList = new ArrayList<>();
        List<ElseInstruction> elseIList = new ArrayList<>();
        for (int i = 0; i < labeledChunkList.size(); i++) {
            List<INode> srlVWithModifiers = srlVNodesMap.get(srlMainVNodes.get(i)); // instruction names
            List<INode> instruction = srlChunkList.get(i);  // extracted instruction

            switch(labeledChunkList.get(i)) {
                case DECL:
                    List<List<INode>> declParameterNodes = srl.findParameterNodes(instruction); // instruction parameters
                    List<DeclarationParameter> declArgs = new ArrayList<>();
                    for (List<INode> arg : declParameterNodes) {
                        DeclarationParameter declA = new DeclarationParameter(arg);
                        declArgs.add(declA);
                    }

                    DeclarationInstruction declI = new DeclarationInstruction(srlVWithModifiers, declArgs);
                    declIList.add(declI);
                    break;

                case DESC:
                    List<List<INode>> descParameterNodes = srl.findParameterNodes(instruction);
                    List<DescriptionParameter> descArgs = new ArrayList<>();
                    for (List<INode> arg : descParameterNodes) {
                        DescriptionParameter descA = new DescriptionParameter(arg);
                        descArgs.add(descA);
                    }

                    DescriptionInstruction descI = new DescriptionInstruction(srlVWithModifiers, descArgs);
                    descIList.add(descI);
                    break;

                case ELSE:
                    List<List<INode>> elseParameterNodes = srl.findParameterNodes(instruction);
                    List<ElseParameter> elseArgs = new ArrayList<>();
                    for (List<INode> arg : elseParameterNodes) {
                        ElseParameter elseA = new ElseParameter(arg);
                        elseArgs.add(elseA);
                    }

                    ElseInstruction elseI = new ElseInstruction(srlVWithModifiers, elseArgs);
                    elseIList.add(elseI);
                    break;
            }
            logger.debug("Build {} from block '{}'", labeledChunkList.get(i), GraphUtils.getUtteranceString(instruction));
        }

        TeachingCommand ts = new TeachingCommand(declIList, descIList, elseIList);
        logger.info("Created TeachingCommand out of merged prediction '{}'.", getMergedClassificationPrediction(ts));
        return ts;
    }

    public String getMergedClassificationPrediction(AbstractCommand cmd) {
        List<MulticlassLabels> labeledPrediction = new ArrayList<>();
        List<DescriptionInstruction> descriptionIList = cmd.getDescriptionInstructions();
        List<DeclarationInstruction> declarationIList = new ArrayList<>();
        List<ElseInstruction> elseIList = new ArrayList<>();

        if (cmd.getClass().equals(TeachingCommand.class)) {
            declarationIList = ((TeachingCommand) cmd).getDeclarationInstructions();
            elseIList = ((TeachingCommand) cmd).getElseInstructions();
        }

        for (INode node : utteranceNodes) {
            if (declarationIList.stream().anyMatch(dcl -> dcl.getInstructionNameNodes().contains(node)) // if in dcl names
                    || (declarationIList.stream().anyMatch(dcl -> dcl.getInstructionParameters().stream()   // if in dcl params
                    .anyMatch(dclP -> dclP.getParameterNodes().contains(node))))) {

                labeledPrediction.add(MulticlassLabels.DECL);

            } else if (descriptionIList.stream().anyMatch(dcl -> dcl.getInstructionNameNodes().contains(node)) // if in desc names
                    || (descriptionIList.stream().anyMatch(dcl -> dcl.getInstructionParameters().stream()   // if in desc params
                    .anyMatch(dclP -> dclP.getParameterNodes().contains(node))))) {

                labeledPrediction.add(MulticlassLabels.DESC);

            } else if (elseIList.stream().anyMatch(dcl -> dcl.getInstructionNameNodes().contains(node)) // if in else names
                    || (elseIList.stream().anyMatch(dcl -> dcl.getInstructionParameters().stream()   // if in else params
                    .anyMatch(dclP -> dclP.getParameterNodes().contains(node))))) {

                labeledPrediction.add(MulticlassLabels.ELSE);

            } else {
                labeledPrediction.add(null);
            }
        }

        return labeledPrediction.toString();
    }
}
