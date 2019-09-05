package edu.kit.ipd.parse.vamos.ontology_mapping;

import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.ontology_connection.method.IMethod;
import edu.kit.ipd.parse.ontology_connection.object.IObject;
import edu.kit.ipd.parse.ontology_connection.object.Object;
import edu.kit.ipd.parse.ontology_connection.parameter.IParameter;
import edu.kit.ipd.parse.ontology_connection.state.IState;
import edu.kit.ipd.parse.ontology_connection.state.State;
import edu.kit.ipd.parse.ontology_connection.value.IValue;
import edu.kit.ipd.parse.ontology_connection.value.Value;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstructionParameter;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionCallCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionNameCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;
import edu.kit.ipd.parse.vamos.utils.MathUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class FunctionCallScorer {

    private static final Logger logger = LoggerFactory.getLogger(FunctionCallScorer.class);

    private static final double FUNCTION_NAME_WEIGHT = 0.6;
    private static final double FUNCTION_PERFECT_MATCH_WEIGHT = 1.5;
    private static final double FUNCTION_PARAM_WEIGHT = 0.4;
    private static final double EXTRACTED_PARAM_WEIGHT = 0.3;

    // some of the primitive datatypes have no ontology representation -> the values could NOT be found through string matched
    private static final String[] TYPES_WITHOUT_ONTOLOGY_REPR = {"String", "int", "double", "float", "short", "char", "boolean", "long"};
    private static final String[] NUMERIC_TYPES = {"int", "double", "float", "short", "long"};
    protected static final StringOntologyMatcher stringMatcher = new StringOntologyMatcher();


    /**
     * Calculate a score for each FunctionCallCandidate according to the extracted ontology method, their params
     * and the params extracted from the input sequence.
     *
     * @param functionCallCandidates candidates for each function call
     * @return list of scored function calls
     */
    public List<FunctionCallCandidate> calculateCombinedScores(List<FunctionCallCandidate> functionCallCandidates) {
        List<FunctionCallCandidate> scoredFunctionCallCandidates = new ArrayList<>();

        // search the candidates for matching ontology parameters of the ontology method
        for (FunctionCallCandidate candidate : functionCallCandidates) {
            logger.debug(">> Calculate score for function call candidate number {}:", functionCallCandidates.indexOf(candidate) + 1);

            IMethod ontologyMethod = candidate.getNameCandidate().getMethodCandidate();   // ontology method corresponding to function name
            Set<IParameter> ontologyMethodParameters = ontologyMethod.getParameters();    // parameters of the ontology method
            List<FunctionParameterCandidate> matchedParameters = candidate.getParameterCandidates();  // matched parameters by string matching
            int numExtractedInstructionParams = candidate.getNameCandidate().getExtractedInstruction().getClearedInstructionParameters().size(); // parameters extracted by the classifiers

            // get each combination of matched-parameter to ontology-parameter mappings
            List<Map<FunctionParameterCandidate, IParameter>> candidatesAllParamCombis = getMatchedParamWithOntologyParamCombinations(
                    matchedParameters, new ArrayList<>(ontologyMethodParameters));

            // handle either no matched parameters or no ontology method parameters
            if (candidatesAllParamCombis.isEmpty()) {
                double score = scoreMethodsWithEmptyParams(candidate.getNameCandidate(), matchedParameters);
                candidate.setFunctionCallScore(score);
                scoredFunctionCallCandidates.add(candidate);

                logger.debug("Found method with no parameters. Calculated score {} for candidate: '{}'", score, candidate.toString());
                continue;
            }

            // build score for each parameter combination
            boolean mappedAnyParam = false;
            for (Map<FunctionParameterCandidate, IParameter> toMap : candidatesAllParamCombis) {

                double parameterScore = 0;  // score for the matched parameter candidates of this map

                Map<FunctionParameterCandidate, IParameter> validMaps = new HashMap<>();
                for (FunctionParameterCandidate paramCandidate : toMap.keySet()) {
                    IParameter ontologyParamToMap = toMap.get(paramCandidate);
                    IIndividual param = paramCandidate.getParameterCandidate();
                    FunctionParameterCandidate temp = new FunctionParameterCandidate(paramCandidate);

                    boolean hasSameDataType = checkDataType(temp, param, ontologyParamToMap);
                    if (hasSameDataType) {
                        parameterScore += temp.getSimilarityScore();
                        validMaps.put(temp, ontologyParamToMap);

                        logger.debug("Found valid parameter match! ['{}' : '{}']", (temp.isPrimitiveType() ?
                                temp.getExtractedParameter().getParameterName() : param.getName()), ontologyParamToMap.getName());
                        continue;  // to skip following debug message
                    }
                    logger.debug("Not matchable!");
                }

                if (parameterScore > 0) {   // if some matches found with this map -> calculate score
                    FunctionCallCandidate validCandidate = new FunctionCallCandidate(candidate.getNameCandidate(), matchedParameters);
                    validCandidate.setParameterCandidates(new ArrayList<>(validMaps.keySet()));
                    validCandidate.setMatchingOntologyParametersMap(validMaps);

                    double score = calculateFunctionCallScore(candidate.getNameCandidate().getSimilarityScore(), parameterScore,
                            numExtractedInstructionParams, validMaps.size(), ontologyMethodParameters.size());
                    validCandidate.setFunctionCallScore(score);

                    logger.debug("Calculated score {} for candidate: '{}'", score, validCandidate.toString());
                    scoredFunctionCallCandidates.add(validCandidate);
                    mappedAnyParam = true;
                }
            }

            // if none of the existing parameters could be mapped, add function call candidate with empty param list
            if (!mappedAnyParam && candidate.getNameCandidate().getSimilarityScore() > 0.8) {

                double score = calculateFunctionCallScore(candidate.getNameCandidate().getSimilarityScore(), 0,
                        numExtractedInstructionParams, 0, ontologyMethodParameters.size());
                candidate.setFunctionCallScore(score);
                scoredFunctionCallCandidates.add(candidate);

                logger.debug("Could not find any parameters for method '{}' with high similarity score. Calculated score {} for " +
                                "candidate '{}'.", candidate.getNameCandidate().getExtractedInstruction().getInstructionName(), score, candidate.toString());
            }
        }

        scoredFunctionCallCandidates = removeDuplicateCandidates(scoredFunctionCallCandidates);

        logger.debug("Calculated scores for all {} function call candidates.", scoredFunctionCallCandidates.size());
        return scoredFunctionCallCandidates;
    }

    private double scoreMethodsWithEmptyParams(FunctionNameCandidate nameCandidate, List<FunctionParameterCandidate> matchedParameters) {
        IMethod ontologyMethod = nameCandidate.getMethodCandidate();
        Set<IParameter> ontologyMethodParameters =  nameCandidate.getMethodCandidate().getParameters();
        List<AbstractInstructionParameter> instructionParams = nameCandidate.getExtractedInstruction().getClearedInstructionParameters();

        // if ontology method needs no params, check the method name  (e.g. liftHead())
        if (ontologyMethodParameters.isEmpty()) {

            // if true, successfully mapped param to function name -> numValidMappedParams = 1, numOntoMethodParams = 1
            if (matchedParameters.stream().anyMatch(p -> (stringMatcher.checkForSubstring(ontologyMethod.getName(),
                    p.getParameterCandidate().getName())))
                    || instructionParams.stream().anyMatch(p -> (stringMatcher.checkForSubstring(ontologyMethod.getName(),
                    p.getClearedNominalizedParameterName())))) {

                return calculateFunctionCallScore(nameCandidate.getSimilarityScore(), 1, instructionParams.size(),
                        1, 1);
            }
        }

        // else calculate score only based on function name score
        return calculateFunctionCallScore(nameCandidate.getSimilarityScore(), 0, instructionParams.size(),
                0, ontologyMethodParameters.size());
    }

    /**
     * Check if datatypes of compared elements (matched parameter candidate and ontology parameter) are equal.
     *
     * @param paramCandidate FunctionParameterCandidate data structure of the matched parameter candidate
     * @param param matched parameter candidate
     * @param ontologyParamToMap parameter of the ontology
     * @return true if match found
     */
    private boolean checkDataType(FunctionParameterCandidate paramCandidate, IIndividual param, IParameter ontologyParamToMap) {

        if (paramCandidate.isPrimitiveType()) {
            logger.debug("Try to match parameter '{}' (primitive) to ontology param '{}' (type '{}').", paramCandidate.getExtractedParameter().getParameterName(),
                    ontologyParamToMap.getName(), ontologyParamToMap.getDataType().getName());

            // check if both are numbers (pos-tag CD for cardinal number)
            if (Arrays.asList(NUMERIC_TYPES).contains(ontologyParamToMap.getDataType().getName())) {
                return paramCandidate.getExtractedParameter().getParameterNodes().stream()
                        .anyMatch(node -> node.getAttributeValue("pos").toString().equals("CD"));
            }

            return Arrays.asList(TYPES_WITHOUT_ONTOLOGY_REPR).contains(ontologyParamToMap.getDataType().getName());
        }

        logger.debug("Try to match extracted parameter '{}' (class '{}') to ontology param '{}' (type '{}').", param.getName(), param.getClass(), ontologyParamToMap.getName(), ontologyParamToMap.getDataType().getName());

        if (param.getClass().equals(Object.class)) {
            IObject objectCandidate = (IObject) param;

            // object has the same data type as the ontology parameter
            if (ontologyParamToMap.getDataType().getName().equals("Object")) return true;

            // object has the same special data type (e.g. openable) as the ontology parameter
            IObject typedObject = stringMatcher.checkForTypedObjects(objectCandidate.getName(), ontologyParamToMap.getDataType());
            if (typedObject != null) return true;

            // one of the sub-objects of this object have the same data type
            if (objectCandidate.hasSubObjects() && objectCandidate.getSubObjects().stream().anyMatch(subobj ->
                    (stringMatcher.checkForTypedObjects(subobj.getName(), ontologyParamToMap.getDataType()) != null))) {
                List<IObject> subObject = objectCandidate.getSubObjects().stream()
                        .filter(subObj -> subObj.getTypes().contains(ontologyParamToMap.getDataType().getName()))
                        .collect(Collectors.toList());
                paramCandidate.setParameterCandidate(subObject.get(0)); // get first sub object and return it
                logger.debug("Found matching subobject '{}' of object '{}'.", subObject.get(0).getName(), objectCandidate.getName());
                return true;
            }

        } else if (param.getClass().equals(Value.class)) {
            IValue valueCandidate = (IValue) param;

            // ontology parameter is no typed object
            if (!ontologyParamToMap.getDataType().isPrimitive()) return false;

            // value fits the ontology parameters' values
            logger.debug("VALUEMATCH: {}", ontologyParamToMap.getDataType().getValues().contains(valueCandidate));
            return ontologyParamToMap.getDataType().getValues().contains(valueCandidate);

        } else if (param.getClass().equals(State.class)) {
            IState statusCandidate = (IState) param;

            // state fits the ontology parameters' states
            logger.debug("STATEMATCH: {}", ontologyParamToMap.getDataType().getName().equals(statusCandidate.getName()));
            return ontologyParamToMap.getDataType().getName().equals(statusCandidate.getName());
        }

        return false;
    }


    /**
     * Find all possible combinations between the set of matched params and ontology params.
     * Example: candidate [a,b,c], ontology params [i,j] -> [a:i,b:j,c:null], [a:null,b:i,c:j], [a:j,b:null,c:i] ...
     * Constraint: |num of candidate|-elements per chunk
     *
     * @param matchedParams params matched by the string matcher
     * @param ontologyParams params from the current ontology method
     * @return list of combinations (map) between these two lists
     */
    private List<Map<FunctionParameterCandidate, IParameter>> getMatchedParamWithOntologyParamCombinations(List<FunctionParameterCandidate> matchedParams,
                                                                                                           List<IParameter> ontologyParams) {
        List<Map<FunctionParameterCandidate, IParameter>> combinationsMap = new ArrayList<>();

        if (matchedParams.size() > 0 && ontologyParams.size() > 0) {
            List<Map<FunctionParameterCandidate, IParameter>> maps = MathUtils.cartesianProductTwoTypes(matchedParams, ontologyParams);

            logger.debug("Check all {} possible combinations for the set of {} matched param candidates and {} ontology params.", maps.size(), matchedParams.size(), ontologyParams.size());

            for (Map<FunctionParameterCandidate, IParameter> map : maps) { // remove null mappings
                Map<FunctionParameterCandidate, IParameter> mapNoNulls = new HashMap<>();
                for (FunctionParameterCandidate c : map.keySet()) {
                    if (map.get(c) != null)  {
                        mapNoNulls.put(c, map.get(c));

//                        logger.debug("Check combination of param candidate '{}' with ontology param '{}'",
//                                (c.isPrimitiveType() ? "isPrimitive" : c.getParameterCandidate().getName()), map.get(c).getName());
                    }
                }
                combinationsMap.add(mapNoNulls);
            }
        }

        return combinationsMap;
    }

    /**
     * Calculates the score for the mapping between matched function name and parameters and ontology individuals.
     *
     * @param nameScore string similarity score for the matched function
     * @param paramScore string similarity score of the matched parameters
     * @param numOntoMethodParams number of method parameters of the current ontology method
     * @param numExtractedInstructionParams number of extracted instruction parameters by the classifier
     * @param numValidMappedParams number of successfully mapped parameters
     * @return score of this mapping
     */
    private double calculateFunctionCallScore(double nameScore, double paramScore, int numExtractedInstructionParams,
                                              int numValidMappedParams, int numOntoMethodParams) {
        double score;
        double perfectMatchBonus = 1;
        if (nameScore > 0.9) perfectMatchBonus = FUNCTION_PERFECT_MATCH_WEIGHT; // add bonus for perfect matched methods

        if (numOntoMethodParams > 0) { // prevent penalization of methods without params
            // score for covering the extracted params by the classifier
            double extractedToNeededDiff = numExtractedInstructionParams - numOntoMethodParams;
            double extractedToNeededWeight = extractedToNeededDiff / ((double) numExtractedInstructionParams); // normalizing on num extracted params
            double extractedToNeededPen = EXTRACTED_PARAM_WEIGHT * (extractedToNeededWeight <= 0 ? 0 : extractedToNeededWeight);

            // score for covering the mapped function params AND the ontology function params
            double mappedToNeededWeight = ((double) numValidMappedParams) / ((double) numOntoMethodParams);

            // weigh the param score with these
            double weightedParamScore = paramScore * mappedToNeededWeight - extractedToNeededPen;

            score = FUNCTION_NAME_WEIGHT * perfectMatchBonus * nameScore + FUNCTION_PARAM_WEIGHT * weightedParamScore;
            logger.debug("Score = {} = {} * name score ({}{}) + {} * param score ({}) * weight ({}) - pen ({}) ",
                    score, FUNCTION_NAME_WEIGHT, nameScore, perfectMatchBonus > 1 ? (" bonus *" + perfectMatchBonus) : "", FUNCTION_PARAM_WEIGHT,
                    paramScore, mappedToNeededWeight, extractedToNeededPen);

        } else { // method has no params
            score = FUNCTION_NAME_WEIGHT * perfectMatchBonus * nameScore;
            logger.debug("Score = {} = {} * name score ({}{}) + {} * param score ({})",
                    score, FUNCTION_NAME_WEIGHT, nameScore, perfectMatchBonus > 1 ? (" bonus *" + perfectMatchBonus) : "", FUNCTION_PARAM_WEIGHT,
                    paramScore);
        }

        return score;
    }

// TODO alternative Bewertungsfunktion @sebastian
// private double calculateFunctionCallScore(double nameScore, double paramScore, int numExtractedInstructionParams,
//                                   int numValidMappedParams, int numOntoMethodParams) {
//
//        if (paramScore == 0) paramScore = 1;
//
//        int missingMappedParams = numOntoMethodParams - numValidMappedParams;
//        double mappedParamPen = numOntoMethodParams == 0 ? 0 : ((double) missingMappedParams / (double) numOntoMethodParams);
//
//        int missingExtractedParams = numExtractedInstructionParams - numValidMappedParams;
//        double extractedParamPen = numExtractedInstructionParams == 0 ? 0 : ((double) missingExtractedParams / (double) numExtractedInstructionParams);
//
//        double score = 0.5 * nameScore + 0.5 * paramScore - 0.2 * mappedParamPen - 0.3 * extractedParamPen;
//        logger.debug("Score = {} = 0.5 * name score ({}) + 0.5 * param score ({}) - 0.2 * mappedPen ({}) - 0.3 * extractedPen ({})",
//                score, nameScore, paramScore, mappedParamPen, extractedParamPen);
//        return score;
//    }


    /**
     * The scoring process produces some unnecessary "duplicate" function call candidates:
     * A) for every ontology method with EMPTY params
     * every candidate with the same function name method but different extracted and different matched params
     * is scored (resulting in the same score per extracted param - because they dont get mapped anyways).
     * - but we need just 1 per (function name, extracted param) combination
     *
     * B) for every ontology method with PRIMITIVE params or  SAME INDIVIDUALs
     *  same as above - we need just 1 per (function name, extracted param, primitive) combination
     *
     * @param scoredFunctionCallCandidates scored candidates - with "duplicates"
     * @return scored candidates - without doubled candidates
     */
    private List<FunctionCallCandidate> removeDuplicateCandidates(List<FunctionCallCandidate> scoredFunctionCallCandidates) {
        List<FunctionCallCandidate> filteredCandidates = new ArrayList<>();
        Map<ImmutablePair<IMethod, List<AbstractInstructionParameter>>, FunctionCallCandidate> caseAmap = new HashMap<>();
        List<ImmutablePair<IMethod, Map<FunctionParameterCandidate, IParameter>>> caseBlist = new ArrayList<>();

        for (FunctionCallCandidate candidate : scoredFunctionCallCandidates) {
            IMethod methodCandidate = candidate.getNameCandidate().getMethodCandidate();
            Set<IIndividual> sameIndividuals = methodCandidate.getSameIndividuals();

            List<AbstractInstructionParameter> extractedParameter = new ArrayList<>();
            candidate.getParameterCandidates().forEach(p -> extractedParameter.add(p.getExtractedParameter()));

            // case A: method requires 0 params -> add only 1 per (method AND same individual, extracted param)-combi
            if (methodCandidate.getParameters().isEmpty()) {
                // two matches are equal, if their parameter candidate lists are equal
                if (caseAmap.containsKey(new ImmutablePair<>(methodCandidate, extractedParameter))) continue;
                if (sameIndividuals.stream().anyMatch(same -> caseAmap.containsKey(new ImmutablePair<>((IMethod) same, extractedParameter)))) {
                    continue;
                }
                caseAmap.put(new ImmutablePair<>(methodCandidate, extractedParameter), candidate);
                filteredCandidates.add(candidate);

            // case B: filter out candidates with sameIndividuals-ontology-methods -> unique candidate -> add all
            } else {
                // two matches are equal, if their matched-parameter-To-ontology-parameter map is equal
                if (candidate.getMatchingOntologyParametersMap() != null) {
                    if (caseBlist.stream().anyMatch(pair -> ((methodCandidate.equals(pair.getLeft())
                            && equalHashMap(candidate.getMatchingOntologyParametersMap(), pair.getRight())) || sameIndividuals.contains(pair.getLeft())))) {
                        continue;
                    }
                    caseBlist.add(new ImmutablePair<>(methodCandidate, candidate.getMatchingOntologyParametersMap()));
                    filteredCandidates.add(candidate);

                } else {
                    // add just one candidate with empty matched params
                    if (caseBlist.stream().anyMatch(pair -> ((methodCandidate.equals(pair.getLeft()) && pair.getRight() == null) || sameIndividuals.contains(pair.getLeft())))) {
                        continue;
                    }
                    caseBlist.add(new ImmutablePair<>(methodCandidate, null));
                    filteredCandidates.add(candidate);
                }
            }
        }

        return filteredCandidates;
    }

    private boolean equalHashMap(Map<FunctionParameterCandidate, IParameter> a,  Map<FunctionParameterCandidate, IParameter> b) {
        if (a == null || b == null) return false;

        if (a.keySet().size() != b.keySet().size()) {
            // logger.debug("Sizes dont match. Hashmaps unequal.");
            return false;
        }

        for (FunctionParameterCandidate p : a.keySet()) {

            // collect all matching param candidates
            List<FunctionParameterCandidate> bMatches = b.keySet().stream()
                    .filter(bkey -> (
                            (bkey.getSimilarityScore() == p.getSimilarityScore())
                                    && (bkey.isPrimitiveType() || bkey.getParameterCandidate().equals(p.getParameterCandidate()))
                                    && (bkey.getExtractedParameter().equals(p.getExtractedParameter()))
                                    && (bkey.isPrimitiveType() == p.isPrimitiveType())))
                    .collect(Collectors.toList());

            // if no matching param candidates -> maps are unequal
            if (bMatches.isEmpty()) {
                // logger.debug("Found candidate {} in hashmap a not in hasmap b", p);
                return false;

            // if paramcandidate (key) matches -> look if IParam (value) matches too
            } else {
                List<FunctionParameterCandidate> matchedOnes = bMatches.stream()
                        .filter(bkey -> ((b.get(bkey).equals(a.get(p))))).collect(Collectors.toList());

                // if less matching -> new item -> maps are unequal
                if (matchedOnes.isEmpty()) {
                    // logger.debug("Found some ParamCandidate-IParameter combination from hashmap a not in hasmap b");
                    return false;
                }
            }
        }

        // logger.debug("No mismatch found. Hashmaps equal.");
        return true;
    }

    /**
     * Return top N function call candidates according to the calculated score.
     *
     * @param candidates function call candidates
     * @return top N of those (see in config "TOP_N")
     */
    public List<FunctionCallCandidate> getTopNCandidates(List<FunctionCallCandidate> candidates, int topNcandidates) {

        candidates.sort(Comparator.comparing(FunctionCallCandidate::getFunctionCallScore));
        Collections.reverse(candidates);        // sort descending

        List<FunctionCallCandidate> topN = candidates.size() >= topNcandidates ? candidates.subList(0, topNcandidates) : candidates;

        logger.debug("Found top{} candidates. Highest score: ({}).", topN.size(), candidates.size() > 0 ? candidates.get(0).getFunctionCallScore() : null);
        return topN;
    }


}
