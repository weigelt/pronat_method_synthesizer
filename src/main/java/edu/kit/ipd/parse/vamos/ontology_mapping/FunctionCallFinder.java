package edu.kit.ipd.parse.vamos.ontology_mapping;

import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.ontology_connection.parameter.IParameter;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstructionParameter;
import edu.kit.ipd.parse.vamos.command_representation.DescriptionParameter;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionCallCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionNameCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import edu.kit.ipd.parse.vamos.utils.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class FunctionCallFinder {

    private static final Logger logger = LoggerFactory.getLogger(edu.kit.ipd.parse.vamos.ontology_mapping.FunctionCallFinder.class);
    // some of the primitive datatypes have no ontology representation -> the values could NOT be found through string matched
    private static final String[] TYPES_WITHOUT_ONTOLOGY_REPR = {"String", "int", "double", "float", "short", "char", "boolean", "long"};
    private static final double SCORE_PRIMITIVE_PARAMS = 0.8;

    /**
     * Create function call candidates consisting of all possible combinations of the
     * found name matches and parameter matches PER instruction.
     *
     * @param functionNameCandidates matches of function names found in the ontology
     * @param functionParameterCandidateList  matches of parameters found in the ontology
     * @return list of function calls
     */
    public List<FunctionCallCandidate> findFunctionCallCandidates(List<FunctionNameCandidate> functionNameCandidates,
                                                                  List<List<FunctionParameterCandidate>> functionParameterCandidateList) {

        // for every name candidate of this instruction: create a combination with the given parameters
        List<FunctionCallCandidate> candidates = new ArrayList<>();
        for (FunctionNameCandidate nameCandidate : functionNameCandidates) {

            // check if one of the parameters of the name candidate (ontology method) has a primitive datatype (having no ontology representation)
            Set<IParameter> ontologyMethodParameters = nameCandidate.getMethodCandidate().getParameters();
            int numOfPrimitiveOParams = (int) ontologyMethodParameters.stream()
                    .filter(p -> Arrays.asList(TYPES_WITHOUT_ONTOLOGY_REPR).contains(p.getDataType().getName())).count();

            // no parameters with primitive datatype -> add 1 Function Call PER paramCombination
            if (numOfPrimitiveOParams == 0) {
                // find all combinations for every parameter candidate of this instruction
                functionParameterCandidateList = functionParameterCandidateList.stream().filter(list -> list.size() > 0).collect(Collectors.toList()); // remove empty param candidates
                List<List<FunctionParameterCandidate>> paramCombinations = MathUtils.cartesianProductListOfLists(functionParameterCandidateList);

                for (List<FunctionParameterCandidate> paramCombination : paramCombinations) {
                    candidates.add(new FunctionCallCandidate(nameCandidate, paramCombination));
                }
                // no parameter ontology mappings for this instruction  -> add 1 empty Function Call
                if (paramCombinations.isEmpty())  candidates.add(new FunctionCallCandidate(nameCandidate, new ArrayList<>()));

            } else { // parameters with primitive datatype -> add n Function Calls

                List<AbstractInstructionParameter> instructionParameters = nameCandidate.getExtractedInstruction().getClearedInstructionParameters();
                if (functionParameterCandidateList.stream().filter(list -> !list.isEmpty()).count() == 0) { // if no parameter candidates
                    // no mapped parameter candidates but extracted instruction parameters -> add 1 primitive Function Call
                    if (instructionParameters.size() > 0) {
                        List<FunctionParameterCandidate> newPrimitiveParams = new ArrayList<>();
                        instructionParameters.forEach(p -> newPrimitiveParams.add(new FunctionParameterCandidate(1, null, p)));
                        newPrimitiveParams.forEach(p -> p.setPrimitiveType(true));
                        candidates.add(new FunctionCallCandidate(nameCandidate, newPrimitiveParams));

                        logger.debug("Created 1 function call candidate for function with primitive params and empty ontology parameter mappings.");

                    } else { // no extracted parameter candidates  -> add 1 empty Function Call
                        candidates.add(new FunctionCallCandidate(nameCandidate, new ArrayList<>()));
                        logger.debug("Created 1 function call candidate for function with primitive params and no extracted parameters.");
                    }

                } else {  //  n parameter candidates -> add 1 primitive Function Call per candidate
                    List<FunctionCallCandidate> primitiveParamCombination = addPrimitiveToParamCombination(nameCandidate, functionParameterCandidateList, numOfPrimitiveOParams);
                    candidates.addAll(primitiveParamCombination);

                    candidates.add(getCombinedExtractedParametersFunctionCall(nameCandidate, instructionParameters));
                }
            }
        }
        logger.debug("Found {} possible function call candidates from {} name candidates and their parameter candidates by string matching.",
                candidates.size(), functionNameCandidates.size());
        return candidates;
    }


    private FunctionCallCandidate getCombinedExtractedParametersFunctionCall(FunctionNameCandidate nameCandidate, List<AbstractInstructionParameter> instructionParameters) {
        // concat all extracted params and add 1 "big" function call candidate
        List<INode> allParamNodesConcat = new ArrayList<>();
        instructionParameters.forEach(p -> allParamNodesConcat.addAll(p.getParameterNodes()));
        AbstractInstructionParameter allParamConcat = new DescriptionParameter(allParamNodesConcat);
        allParamConcat.setClearedParameterNodes(allParamNodesConcat);
        allParamConcat.setClearedNominalizedParameterName(GraphUtils.getUtteranceString(allParamNodesConcat));

        List<FunctionParameterCandidate> newPrimitiveParamsConcat = new ArrayList<>();
        newPrimitiveParamsConcat.add(new FunctionParameterCandidate(1, null, allParamConcat));
        newPrimitiveParamsConcat.forEach(p -> p.setPrimitiveType(true));

        return new FunctionCallCandidate(nameCandidate, newPrimitiveParamsConcat);
    }

    /**
     * Handle datatypes of ontology parameters which have no ontology individual representation.
     * If the datatype is one of the {@link this.TYPES_WITHOUT_ONTOLOGY_REPR}, the parameters in the
     * user input could not be found by string matching!  (e.g. String "hello" or int "3")
     *
     * In this case, no correct parameter-to-ontology-element match can be found per string matching.
     * But sometimes (invalid) parameter candidates mappings ARE found (e.g. String "kitchen" to Object "kitchen").
     * Therefore, if a method has parameters with such datatypes, the detected parameter mappings have to be nulled out.
     * -> additional Function Call Candidates have to be added
     *
     * @param nameCandidate current function mapping
     * @param functionParameterCandidateList  list of parameters for this function name candidate
     * @param numOfPrimitiveOParams   number of parameters with primitive datatype in this current function mapping method
     * @return list of updated function call candidates with some nulled out parameter candidate mappings
     */
    private List<FunctionCallCandidate> addPrimitiveToParamCombination(FunctionNameCandidate nameCandidate, List<List<FunctionParameterCandidate>>
            functionParameterCandidateList, int numOfPrimitiveOParams) {
        List<FunctionCallCandidate> primitiveParamCandidates = new ArrayList<>();

        // if only 1 single parameter candidate (ontology mapping) exist -> add 1 Function Call
        if (functionParameterCandidateList.stream().filter(list -> !list.isEmpty()).count() == 1) {
            FunctionParameterCandidate singleCandidate = functionParameterCandidateList.stream()
                    .flatMap(List::stream).collect(Collectors.toList()).get(0); // just one parameter could be matched to ontology

            FunctionParameterCandidate primitiveParam = new FunctionParameterCandidate(SCORE_PRIMITIVE_PARAMS, null, singleCandidate.getExtractedParameter());
            primitiveParam.setPrimitiveType(true); // set parameter primitiveType
            List<FunctionParameterCandidate> newPrimitiveParams = new ArrayList<>();
            newPrimitiveParams.add(primitiveParam);

            primitiveParamCandidates.add(new FunctionCallCandidate(nameCandidate, newPrimitiveParams));
            logger.debug("Created 1 function call candidate with nulled out parameter candidate for the primitve method parameter.");

        } else { // if more parameters candidates exist -> add n Function Calls

            // find all combinations for every parameter candidate of this instruction
            functionParameterCandidateList = functionParameterCandidateList.stream().filter(list -> list.size() > 0).collect(Collectors.toList()); // remove empty candidates
            List<List<FunctionParameterCandidate>> paramCombinations = MathUtils.cartesianProductListOfLists(functionParameterCandidateList);
            for (List<FunctionParameterCandidate> paramCombination : paramCombinations) {

                // create placeholder and get all combinations of placeholder and params
                List<String> primitivePlaceholder = new ArrayList<>();
                for (int i = 0; i < Math.min(numOfPrimitiveOParams, paramCombination.size()); i++) {
                    primitivePlaceholder.add("makeParameterCandidatePrimitive");
                }
                List<Map<FunctionParameterCandidate, String>> maps = MathUtils.cartesianProductTwoTypes(paramCombination, primitivePlaceholder);

                // for every combi-map: set exact numOfPrimitives of the mapped parameter candidates to null to match the current ontology method
                for (Map<FunctionParameterCandidate, String> combi : maps) {

                    List<FunctionParameterCandidate> updatedParamCandidates = new ArrayList<>();
                    for (FunctionParameterCandidate c : combi.keySet()) {

                        if (combi.get(c) != null) { // makeParameterCandidatePrimitive
                            // null the mapped parameter candidate but remain the original extracted InstructionParameter
                            FunctionParameterCandidate newPrimitiveParam = new FunctionParameterCandidate(SCORE_PRIMITIVE_PARAMS, null, c.getExtractedParameter());
                            newPrimitiveParam.setPrimitiveType(true);  // flag that the param candidate is null and the instruction parameters should be used instead
                            updatedParamCandidates.add(newPrimitiveParam);
                        } else {
                            // if more than numOfPrimitives mapped parameter candidate, add them again (unchanged)
                            updatedParamCandidates.add(c);
                        }
                    }
                    primitiveParamCandidates.add(new FunctionCallCandidate(nameCandidate, updatedParamCandidates));
                }
            }
            logger.debug("Created {} function call candidates with each {} primitive parameter candidates ({} primitive datatypes in current method candidate)",
                    primitiveParamCandidates.size(), numOfPrimitiveOParams, numOfPrimitiveOParams);
        }

        return primitiveParamCandidates;
    }

}
