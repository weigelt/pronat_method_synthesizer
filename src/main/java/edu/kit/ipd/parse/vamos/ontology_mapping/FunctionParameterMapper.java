package edu.kit.ipd.parse.vamos.ontology_mapping;

import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.vamos.MethodSynthesizer;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstruction;
import edu.kit.ipd.parse.vamos.command_representation.AbstractInstructionParameter;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import edu.kit.ipd.parse.vamos.utils.MathUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FunctionParameterMapper {

	private static final Logger logger = LoggerFactory.getLogger(FunctionParameterMapper.class);
	private static final double SYNONYM_NAME_WEIGHT = 0.6;
	private static final String defaultPosTag = "NN";
	private static String[] parameterPosTags;
	private static final String defaultStopWord = "how";
	private static String[] stopWords;
	private static StringOntologyMatcher stringMatcher;
	private boolean useSynonyms;

	public FunctionParameterMapper(boolean useContext, boolean usePermutations) {
        useSynonyms = useContext;

		Properties props = ConfigManager.getConfiguration(MethodSynthesizer.class);
		parameterPosTags = props.getProperty("PARAMETER_POS").split(",");
		parameterPosTags = parameterPosTags.length == 0 ? new String[] { defaultPosTag }
				: Arrays.stream(parameterPosTags).map(String::trim).toArray(String[]::new);
		stopWords = props.getProperty("STOP_WORDS").split(",");
		stopWords = stopWords.length == 0 ? new String[] { defaultStopWord }
				: Arrays.stream(stopWords).map(String::trim).toArray(String[]::new);

		stringMatcher = new StringOntologyMatcher(usePermutations);
	}

	/**
	 * Find possible function parameter candidates for the extracted instruction
	 * matching in the connected ontology.
	 * 
	 * @param instructionParams
	 *            extracted parameters
	 * @return list of possible matching ontology parameters, each given with a
	 *         double similarity score
	 */
	protected <U extends AbstractInstructionParameter> List<List<FunctionParameterCandidate>> findFunctionParameterCandidates(
			List<U> instructionParams) {
		List<List<FunctionParameterCandidate>> parameterCandidateList = new ArrayList<>();

		for (U param : instructionParams) {
			String name = param.getClearedNominalizedParameterName();
			logger.debug("Find function parameter candidates for extracted function parameter '{}'.", name);

			// more than 1 word per parameter name -> add all possible permutations of names
			List<String> combinedNames = new ArrayList<>();
			if (name.split(" ").length > 1) {
				combinedNames.addAll(permuteWordsInString(name.split(" ")));
			} else {
				combinedNames.add(name);
			}

			// get similarity score for each parameter name permutation
			List<ImmutablePair<Double, IIndividual>> parameterMatches = stringMatcher.getParameterEnsembleMatches(combinedNames);

			// create FunctionNameCandidate data structure
			List<FunctionParameterCandidate> parameterCandidates = new ArrayList<>();
			for (ImmutablePair<Double, IIndividual> match : parameterMatches) {
				parameterCandidates.add(new FunctionParameterCandidate(match.getKey(), match.getValue(), param));
			}

			// get perfect matches from instruction parameter synonyms
			if (useSynonyms) {
				List<ImmutablePair<Double, IIndividual>> synonymMatches = getPerfectSynonymMatches(param.getClearedParameterNodes());
				for (ImmutablePair<Double, IIndividual> synonymMatch : synonymMatches) {
					// decrease the influence of synonym matches
					parameterCandidates.add(
							new FunctionParameterCandidate((SYNONYM_NAME_WEIGHT * synonymMatch.getKey()), synonymMatch.getValue(), param));
				}
			}

			parameterCandidateList.add(parameterCandidates);

			logger.debug("Found {} function parameter candidates for instruction parameter '{}'.", parameterCandidates.size(),
					param.toString());
		}

		return parameterCandidateList;
	}

	/**
	 * Preprocess the extracted instruction parameters. Filter relevant part of
	 * speech words and remove stopwords.
	 * 
	 * @param instruction
	 *            current instruction
	 */
	public <T extends AbstractInstruction<U>, U extends AbstractInstructionParameter> void preprocessFunctionParameters(T instruction) {
		List<U> clearedInstructionParams = new ArrayList<>();
		List<U> instructionParameters = instruction.getInstructionParameters();

		for (U param : instructionParameters) {

			U clearedDscP = getCorefAndFilterPosAndRemoveStopwords(param);

			// not useful parameter (like "how to") -> continue with next one
			if (!clearedDscP.getClearedParameterNodes().isEmpty()) {
                clearedInstructionParams.add(clearedDscP);
            }
		}

		instruction.setClearedInstructionParameters(clearedInstructionParams);
		logger.debug("Preprocessing the function parameters lead from {} to {} remaining parameters.", instructionParameters.size(),
				clearedInstructionParams.size());
	}

	/**
	 * Whitelist relevant part of speech tags for instruction parameters and remove
	 * stopwords.
	 *
	 * @param instructionParam
	 *            current parameters
	 * @return cleared DescriptionParameter datastructure
	 */
	private <U extends AbstractInstructionParameter> U getCorefAndFilterPosAndRemoveStopwords(U instructionParam) {
		List<INode> clearedNodes = new ArrayList<>();
		StringJoiner clearedName = new StringJoiner(" ");

		for (INode node : instructionParam.getParameterNodes()) {

			// get coreference for the current node (word)
			String parameterCoreference = getParameterCoreference(node);
			if (!parameterCoreference.equals("") && !Arrays.asList(stopWords).contains(parameterCoreference)) {
				clearedName.add(parameterCoreference);
				clearedNodes.add(node);
				continue;
			}

			// remove unnecessary part of speech tags
			String partOfSpeech = (String) node.getAttributeValue("pos");
			if (partOfSpeech != null && Arrays.asList(parameterPosTags).contains(partOfSpeech)) {
				String word = (String) node.getAttributeValue("value");

				// get lemma (singular of NN)
				if (node.getAttributeValue("lemma") != null) {
					word = (String) node.getAttributeValue("lemma");
				}

				// remove stopwords
				if (!Arrays.asList(stopWords).contains(word)) {
					clearedName.add(word);
					clearedNodes.add(node);
				}
			}
		}
		instructionParam.setClearedParameterNodes(clearedNodes);
		instructionParam.setClearedNominalizedParameterName(clearedName.toString());

		logger.debug("Cleared parameter name '{}' to '{}'.", instructionParam.getParameterName(),
				instructionParam.getClearedNominalizedParameterName());
		return instructionParam;
	}

	/**
	 * The parameter candidates of multiple DeclarationInstructions (for building
	 * the method signature) are handled as synonyms. Therefore only new, unseen
	 * candidates should be added to the list.
	 *
	 * @param mainList
	 *            already detected function parameter candidates
	 * @param synonymList
	 *            new detected function parameter candidates
	 * @return merged list of parameter candidates
	 */
	public List<List<FunctionParameterCandidate>> addWithoutDuplicates(List<List<FunctionParameterCandidate>> mainList,
			List<List<FunctionParameterCandidate>> synonymList) {
		List<FunctionParameterCandidate> newItems = new ArrayList<>();
		for (FunctionParameterCandidate synonym : synonymList.stream().flatMap(List::stream).collect(Collectors.toList())) {
			// skip parameter duplicates
			if (mainList.stream().flatMap(List::stream).anyMatch(c -> c.getParameterCandidate().equals(synonym.getParameterCandidate()))) {
                continue;
            }
			newItems.add(synonym);
		}
		if (!newItems.isEmpty()) {
            mainList.add(newItems);
        }

		return mainList;
	}

	/**
	 * For the given parameter chunk: get all possible permutations.
	 *
	 * @param words
	 *            array of string
	 * @return list of possible word permutations per string
	 */
	private List<String> permuteWordsInString(String[] words) {
		List<List<String>> permutations = new ArrayList<>();
		List<String> result = new ArrayList<>();
		MathUtils.deptFirstSearch(words, permutations, result);

		List<String> permutationsResult = new ArrayList<>();
		for (List<String> p : permutations) {
			StringJoiner concatenatedWords = new StringJoiner(" ");
			for (String word : p) {
				concatenatedWords.add(word);
			}
			permutationsResult.add(concatenatedWords.toString());
		}

		logger.debug("Create {} string permutations for better string matching.", permutationsResult.size());
		return permutationsResult;
	}

	/**
	 * Search for perfect matches of function parameter synonyms in the ontology
	 * methods.
	 *
	 * @param clearedParameterNodes
	 *            instruction nodes (nouns)
	 * @return perfect synonym matches
	 */
	private List<ImmutablePair<Double, IIndividual>> getPerfectSynonymMatches(List<INode> clearedParameterNodes) {
		List<String> synonyms = getParameterSynonyms(clearedParameterNodes);
		List<ImmutablePair<Double, IIndividual>> matches = stringMatcher.getParameterEnsembleMatches(synonyms);

		// only return (almost) perfect synonym matches
		matches = matches.stream().filter(match -> match.getKey() > 0.9f).collect(Collectors.toList());

		if (matches.size() > 0) {
			StringJoiner joiner = new StringJoiner("; ");
			matches.forEach(m -> joiner.add(m.getRight().getName()));
			logger.debug("Found {} perfect synonym matches of function parameter name '{}': '{}'", matches.size(),
					GraphUtils.getUtteranceString(clearedParameterNodes), joiner.toString());
		}
		return matches;
	}

	/**
	 * Extract wordnet synonyms of the noun parameters of one instruction. Use the
	 * synonyms from the {@link edu.kit.ipd.parse.contextanalyzer.ContextAnalyzer}.
	 *
	 * @param nodes
	 *            instruction parameter nodes (nouns)
	 * @return list of synonyms
	 */
	private List<String> getParameterSynonyms(List<INode> nodes) {
		List<String> synonyms = new ArrayList<>();

		for (INode node : nodes) {
			List<? extends IArc> arcs = node.getIncomingArcsOfType(GraphUtils.getReferenceArcType());
			for (IArc arc : arcs) {
				if (arc.getSourceNode().getType().equals(GraphUtils.getContextEntityNodeType())) {
					INode contextNode = arc.getSourceNode();
					synonyms.addAll(GraphUtils.getListFromArrayToString((String) contextNode.getAttributeValue("synonyms")));
				}
			}
		}

		logger.debug("Found {} synonyms of function parameter '{}': '{}'", synonyms.size(), GraphUtils.getUtteranceString(nodes),
				Arrays.toString(synonyms.toArray()));
		return synonyms;
	}

	/**
	 * Extract coreference information of the noun parameters. Use the
	 * contextRelation tags from the
	 * {@link edu.kit.ipd.parse.contextanalyzer.ContextAnalyzer}. Only use the
	 * pronominal anaphora and object resolution.
	 *
	 * @param node
	 *            instruction parameter node (nouns)
	 * @return list of coref nouns (e.g. "it" -> "the table")
	 */
	private String getParameterCoreference(INode node) {
		String coreference = "";

		if (node.getAttributeValue("pos").equals("DT")) {
            return coreference; // skip these
        }

		List<? extends IArc> contextArcs = node.getIncomingArcsOfType(GraphUtils.getReferenceArcType());
		INode referenceNode = !contextArcs.isEmpty() ? contextArcs.get(0).getSourceNode() : null;
		if (referenceNode == null) {
            return coreference;
        }

		if (!contextArcs.isEmpty() && referenceNode.getType().equals(GraphUtils.getContextEntityNodeType())) {
			List<? extends IArc> corefArcs = referenceNode.getOutgoingArcsOfType(GraphUtils.getContextRelationArcType());

			double confidence = 0;
			for (IArc rel : corefArcs) {
				if (rel.getAttributeValue("typeOfRelation").equals("referentRelation")) {

					if (rel.getAttributeValue("name").equals("anaphoraReferent")
							|| rel.getAttributeValue("name").equals("objectIdentityReferent")) {

						if ((double) rel.getAttributeValue("confidence") > confidence) {

							confidence = (double) rel.getAttributeValue("confidence");
							coreference = (String) rel.getTargetNode().getAttributeValue("name");
						}
					}
				}
			}
		}

		if (!coreference.equals("")) {
            logger.debug("Found coreference '{}' of function parameter '{}'.", coreference, node.getAttributeValue("value"));
        }
		return coreference;
	}
}
