package edu.kit.ipd.parse.vamos.ontology_mapping;

import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.ontology_connection.method.IMethod;
import edu.kit.ipd.parse.vamos.MethodSynthesizer;
import edu.kit.ipd.parse.vamos.command_representation.*;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionNameCandidate;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class FunctionNameMapper {

    private static final Logger logger = LoggerFactory.getLogger(FunctionNameMapper.class);
    private static StringOntologyMatcher stringMatcher;
    private boolean useSynonyms;
    private static final double SYNONYM_NAME_WEIGHT = 0.5;
    private static final String defaultStopWord = "mean";
    private static String[] stopWords;

    public FunctionNameMapper(boolean useContext) {
        this.useSynonyms = useContext;

        Properties props = ConfigManager.getConfiguration(MethodSynthesizer.class);
        stopWords = props.getProperty("STOP_WORDS").split(",");
        stopWords = stopWords.length == 0 ? new String[]{defaultStopWord} : Arrays.stream(stopWords).map(String::trim).toArray(String[]::new);

        stringMatcher = new StringOntologyMatcher();
    }

    /**
     * Find possible function name candidates for the extracted instruction matching in the connected ontology.
     * @param instruction extracted instruction
     * @return list of possible matching ontology methods, each ImmutablePaired with a double similarity score
     */
    protected List<FunctionNameCandidate> findFunctionNameCandidates(AbstractInstruction instruction) {
        logger.debug("Find function name candidates for extracted function name '{}'.", instruction.getInstructionName());

        // get cleared parameter names for function name (!) mapping
        List<String> parameterNames = new ArrayList<>();
        List<AbstractInstructionParameter> parameters = instruction.getClearedInstructionParameters();
        parameters.forEach(i -> parameterNames.add(i.getClearedNominalizedParameterName()));

        // combine these function names with its parameters
        List<String> combinedNames = concatFunctionWithParameterNames(instruction.getClearedLemmatizedInstructionName(), parameterNames);

        // get similarity score for each name combination
        List<ImmutablePair<Double, IMethod>> methodMatches = stringMatcher.getMethodEnsembleMatches(combinedNames);

        // create FunctionNameCandidate data structure
        List<FunctionNameCandidate> nameCandidates = new ArrayList<>();
        for (ImmutablePair<Double, IMethod> match : methodMatches) {
            nameCandidates.add(new FunctionNameCandidate(match.getKey(), match.getValue(), instruction));
        }

        // get perfect matches from instruction name synonyms
        if (useSynonyms) {
            List<ImmutablePair<Double, IMethod>> synonymMatches = getPerfectSynonymMatches(instruction.getClearedInstructionNameNodes());
            for (ImmutablePair<Double, IMethod> synonymMatch : synonymMatches) {
                // decrease the influence of synonym matches
                nameCandidates.add(new FunctionNameCandidate((SYNONYM_NAME_WEIGHT * synonymMatch.getKey()), synonymMatch.getValue(), instruction));
            }
        }

        logger.debug("Found {} function name candidates for '{}'.", nameCandidates.size(), instruction.toString());
        return nameCandidates;
    }

    /**
     * Preprocess the extracted instructions.
     * Remove stopwords and lemmatize function names.
     * @param instructionList list of extracted instructions
     * @return list of cleared instructions
     */
    protected <T extends AbstractInstruction> List<T> preprocessFunctionName(List<T> instructionList) {
        List<T> clearedInstructions = new ArrayList<>();
        for (T instruction : instructionList) {
            T clearedDscI = getLemmasAndRemoveStopwords(instruction);

            // instruction was no function call (like "'means' to", "'thank' you") -> continue with next one
            if (!clearedDscI.getClearedInstructionNameNodes().isEmpty()) clearedInstructions.add(clearedDscI);
        }

        logger.debug("Preprocessing the function names lead from {} to {} remaining instructions.", instructionList.size(), clearedInstructions.size());
        return clearedInstructions;
    }

    /**
     * Concatenates the function name with each one of the parameters.
     * Ensures that function name is always on first place in the String.
     * @param functionName name of the instruction
     * @param parameterNames list of parameters of this instruction
     * @return list of concatenated function names
     */
    private List<String> concatFunctionWithParameterNames(String functionName, List<String> parameterNames) {
        List<String> concatNames = new ArrayList<>();

        concatNames.add(functionName);  // add function name without parameters
        if (functionName.split(" ").length > 1) {
            concatNames.addAll(Arrays.asList(functionName.split(" ")));   // function name > 1 word
        }

        parameterNames.forEach(param -> concatNames.add(functionName + " " + param)); // function name with 1 parameter

        logger.debug("Concat function name '{}' with parameters for better matches. Blow up 1 name to {} combinations (e.g. '{}')",
                functionName, concatNames.size(), (concatNames.size() > 1 ? concatNames.get(1) : "(No params)"));
        return concatNames;
    }

    /**
     * Lemmatize the name (postag: verb) of the instruction and remove possible stopwords.
     * Save it in the datastructure
     * @param instruction current instruction
     * @return lemmatized and cleared DescriptionInstruction
     */
    private <T extends AbstractInstruction> T getLemmasAndRemoveStopwords(T instruction) {
        List<INode> instructionNameNodes = instruction.getInstructionNameNodes();
        List<INode> clearedNodes = new ArrayList<>();
        StringJoiner lemmatizedName = new StringJoiner(" ");

        for (INode node : instructionNameNodes) {
            String lemma;

            if (node.getAttributeValue("lemma") != null) {
                lemma = (String) node.getAttributeValue("lemma");
            } else {
                lemma = (String) node.getAttributeValue("value");
            }

            if (!Arrays.asList(stopWords).contains(lemma)) {
                lemmatizedName.add(lemma);
                clearedNodes.add(node);
            }
        }
        instruction.setClearedInstructionNameNodes(clearedNodes);
        instruction.setClearedLemmatizedInstructionName(lemmatizedName.toString());

        logger.debug("Cleared method name '{}' to '{}'.", instruction.getInstructionName(), instruction.getClearedLemmatizedInstructionName());
        return instruction;
    }


    /**
     * Search for perfect matches of function name synonyms in the ontology methods.
     *
     * @param clearedInstructionNameNodes instruction nodes (verbs)
     * @return perfect synonym matches
     */
    private List<ImmutablePair<Double, IMethod>> getPerfectSynonymMatches(List<INode> clearedInstructionNameNodes) {
        List<String> synonyms = getNameSynonyms(clearedInstructionNameNodes);
        List<ImmutablePair<Double, IMethod>> matches = stringMatcher.getMethodEnsembleMatches(synonyms);

        // only return (almost) perfect synonym matches
        matches = matches.stream().filter(match -> match.getKey() > 0.9f).collect(Collectors.toList());

        if (matches.size() > 0) {
            StringJoiner joiner = new StringJoiner("; ");
            matches.forEach(m -> joiner.add(m.getRight().getName()));
            logger.debug("Found {} perfect synonym matches of function name '{}': '{}'", matches.size(),
                GraphUtils.getUtteranceString(clearedInstructionNameNodes), joiner.toString());
        } else {
            logger.debug("Found no perfect synonym matches.");
        }

        return matches;
    }


    /**
     * Extract wordnet synonyms of the verb(s) of one instruction.
     * Use the synonyms from the {@link edu.kit.ipd.parse.contextanalyzer.ContextAnalyzer}.
     *
     * @param nodes instruction nodes (verbs)
     * @return list of synonyms
     */
    private List<String> getNameSynonyms(List<INode> nodes) {
        List<String> synonyms = new ArrayList<>();

        for (INode node : nodes) {
            List<? extends IArc> arcs = node.getIncomingArcs().stream()
                    .filter(arc -> arc.getType().equals(GraphUtils.getReferenceArcType()))
                    .collect(Collectors.toList());

            for (IArc arc : arcs) {
                if (arc.getSourceNode().getType().equals(GraphUtils.getContextActionNodeType())) {
                    INode contextNode = arc.getSourceNode();
                    synonyms.addAll(GraphUtils.getListFromArrayToString((String) contextNode.getAttributeValue("synonyms")));
                }
            }
        }

        logger.debug("Found {} synonyms of function name '{}': '{}'", synonyms.size(), GraphUtils.getUtteranceString(nodes),
                Arrays.toString(synonyms.toArray()));
        return synonyms;
    }


    /**
     * Removes stopwords from a given string and returns it.
     *
     * @param input string input
     * @return cleared string output
     */
    public String removeStopwordsFromString(String input) {
        for (String stopword : stopWords) {
            if (input.contains(stopword)) {
                input = input.replace(stopword, "");
            }
        }
        return input;
    }
}
