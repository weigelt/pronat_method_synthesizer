package edu.kit.ipd.parse.vamos.ontology_mapping;

import com.google.common.base.CaseFormat;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.vamos.MethodSynthesizer;
import edu.kit.ipd.parse.vamos.command_representation.*;
import edu.kit.ipd.parse.vamos.programm_representation.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class OntologyMapper {

	private static final Logger logger = LoggerFactory.getLogger(OntologyMapper.class);
	private static FunctionNameMapper nameMapper;
	private static FunctionParameterMapper paramMapper;
	private static FunctionCallScorer functionCallScorer;
	private static FunctionCallFinder functionCallFinder;
	private static int topNcandidates;

	public OntologyMapper(boolean useContext, boolean usePermutations, boolean alsoMatchUnlemmatized, boolean considerCoverage,
			double coverageMultiplier) {
		Properties props = ConfigManager.getConfiguration(MethodSynthesizer.class);
		topNcandidates = Integer.parseInt(props.getProperty("TOP_N"));
		logger.info("Read in configuration for TOP_N: return top{} function call candidates.", topNcandidates);

		nameMapper = new FunctionNameMapper(useContext, usePermutations, alsoMatchUnlemmatized);
		paramMapper = new FunctionParameterMapper(useContext, usePermutations);
		functionCallFinder = new FunctionCallFinder();
		functionCallScorer = new FunctionCallScorer(usePermutations, alsoMatchUnlemmatized, considerCoverage, coverageMultiplier);
	}

	/**
	 * Main method to build the Command datastructure with internal mapping of the
	 * extracted elements (method names & parameters) to ontology individuals.
	 *
	 * @param cmd
	 *            Execution or TeachingCommand
	 * @return Command
	 */
	public CommandCandidate findCommandMappingToAPI(AbstractCommand cmd) {
		logger.info(cmd.getClass() + " found. Try to map it to the API");

		if (cmd.getClass().equals(TeachingCommand.class)) {
			TeachingCommand ts = (TeachingCommand) cmd;

			// method head: add declarations
			MethodSignatureCandidate methodSignature = buildMethodSignature(ts.getDeclarationInstructions());
			// todo do sth with ELSE-Block? (e.g. add as decl-synonym?)

			// method body: map to script of descriptions  (for both TeachingCommand AND ExecutionCommand)
			List<List<FunctionCallCandidate>> script = buildScriptOfFunctionCalls(cmd.getDescriptionInstructions());
			return new CommandCandidate(methodSignature, script);

		} else {
			// method body: map to script of descriptions  (for both TeachingCommand AND ExecutionCommand)
			List<List<FunctionCallCandidate>> script = buildScriptOfFunctionCalls(cmd.getDescriptionInstructions());

			return new CommandCandidate(script);
		}

	}

	/**
	 * Build function signature of method head. Treat different declaration
	 * instructions as SYNONYMS.
	 *
	 * @param declarationInstructions
	 *            instructions of the declaration {@link DeclarationInstruction}
	 * @return 1 instruction paired with the best function call candidate
	 */
	private MethodSignatureCandidate buildMethodSignature(List<DeclarationInstruction> declarationInstructions) {
		logger.info("Build function signature for method head.");

		// clear instructions from stopwords / unnecessary instructions
		List<DeclarationInstruction> clearedInstructions = nameMapper.preprocessFunctionName(declarationInstructions);

		// treat names and parameters of the declaration instruction as synonyms
		List<FunctionNameCandidate> functionNameMatches = new ArrayList<>();
		List<List<FunctionParameterCandidate>> functionParameterMatches = new ArrayList<>();
		for (DeclarationInstruction dclI : clearedInstructions) {
			logger.debug("Process new DECLARATION instruction '{}'.", dclI.toString());

			// clear instruction parameters from stopwords / unnecessary parameters
			paramMapper.preprocessFunctionParameters(dclI);

			// find instruction name mappings in ontology
			functionNameMatches.addAll(nameMapper.findFunctionNameCandidates(dclI));

			// find instruction parameter mappings in ontology and remove duplicates from synonymous instruction
			List<List<FunctionParameterCandidate>> parameterCandidates = paramMapper
					.findFunctionParameterCandidates(dclI.getClearedInstructionParameters());
			functionParameterMatches = paramMapper.addWithoutDuplicates(functionParameterMatches, parameterCandidates);
		}

		// define method name: chose the name from the first instruction with not empty-cleared name
		DeclarationInstruction dclI;
		String methodName;
		if (!clearedInstructions.isEmpty()) {
			dclI = clearedInstructions.get(0); // get any
			methodName = dclI.getClearedLemmatizedInstructionName();
			methodName = StringUtils.uncapitalize(WordUtils.capitalizeFully(methodName).replaceAll(" ", ""));
		} else {
			dclI = declarationInstructions.get(0); // get any
			methodName = "NAME_PLACEHOLDER_" + dclI.getInstructionName();
			logger.error("Could not find any proper instruction name (is in stopwords list).");
		}
		StringBuilder methodNameBuilder = new StringBuilder(methodName);

		// check if this function name already exists: if higher than 0.95f, the function may already be defined
		checkIfMethodExists(methodNameBuilder, functionNameMatches, functionParameterMatches);

		// try to map the parameters and add them to the method signature
		ImmutablePair<String, List<FunctionParameterCandidate>> signature = createMethodNameAndParameters(dclI, methodNameBuilder,
				functionParameterMatches);

		MethodSignatureCandidate methodSignature = new MethodSignatureCandidate(signature.getLeft(), signature.getRight(), dclI);
		logger.info("Processed all {} DECLARATION instructions. Successfully built method signature: {}", clearedInstructions.size(),
				methodSignature.toString());
		return methodSignature;
	}

	/**
	 * Build script of function calls for method body. Treat different declaration
	 * instructions as DIFFERENT instruction.
	 *
	 * @param descriptionInstructions
	 *            instructions of the description {@link DescriptionInstruction}
	 * @return each instruction paired with the best function call candidate
	 */
	private List<List<FunctionCallCandidate>> buildScriptOfFunctionCalls(List<DescriptionInstruction> descriptionInstructions) {
		logger.info("Build script of function calls for method body.");
		List<List<FunctionCallCandidate>> functionCalls = new ArrayList<>();

		// clear instructions from stopwords / unnecessary instructions
		List<DescriptionInstruction> clearedInstructions = nameMapper.preprocessFunctionName(descriptionInstructions);

		int instructionCountToMap = clearedInstructions.size();
		int instructionCountMapped = 0;
		for (DescriptionInstruction dscI : clearedInstructions) {
			logger.debug("Process new DESCRIPTION instruction '{}'.", dscI.toString());

			// clear instruction parameters from stopwords / unnecessary parameters
			paramMapper.preprocessFunctionParameters(dscI);

			// find instruction name mappings in ontology
			List<FunctionNameCandidate> functionNameMatches = nameMapper.findFunctionNameCandidates(dscI);

			// find instruction parameter mappings in ontology
			List<List<FunctionParameterCandidate>> functionParameterMatches = paramMapper
					.findFunctionParameterCandidates(dscI.getClearedInstructionParameters());

			// calculate combined score of instruction name and parameter ontology matches
			List<FunctionCallCandidate> functionCallCandidates = functionCallFinder.findFunctionCallCandidates(functionNameMatches,
					functionParameterMatches);

			// score each candidate and add the best scored candidate
			List<FunctionCallCandidate> scoredCandidates = functionCallScorer.calculateCombinedScores(functionCallCandidates);
			List<FunctionCallCandidate> topNCandidates = functionCallScorer.getTopNCandidates(scoredCandidates, topNcandidates);
			if (!topNCandidates.isEmpty()) {
				logger.debug("Successfully mapped DESCRIPTION instruction '{}' to highest scored function call '{}'.", dscI.toString(),
						topNCandidates.get(0).toString());
				functionCalls.add(topNCandidates);
				instructionCountMapped++;
			} else {
				logger.error("Could not map DESCRIPTION instruction '{}' to any ontology function call. Add empty list.", dscI.toString());
				functionCalls.add(new ArrayList<>());
			}
		}

		if (instructionCountMapped != instructionCountToMap) {
			logger.error("Could only map {} of {} functions", instructionCountMapped, instructionCountToMap);
		} else {
			logger.info("Successfully mapped all {} DESCRIPTION instructions to functions of the method body.", functionCalls.size());
		}

		return functionCalls;
	}

	/**
	 * Checks if the given method candidate (name candidates and parameter
	 * candidates) already exists in the ontology (if similarity score > 0.9). If
	 * yes, add the prefix "NAMECONFLICT_" to the method name.
	 *
	 * @param methodName
	 *            current method name
	 * @param functionNameMatches
	 *            function name cndidates
	 * @param functionParameterMatches
	 *            function parameter candidates
	 */
	private void checkIfMethodExists(StringBuilder methodName, List<FunctionNameCandidate> functionNameMatches,
			List<List<FunctionParameterCandidate>> functionParameterMatches) {
		List<FunctionNameCandidate> similarMethodNames = functionNameMatches.stream().filter(c -> c.getSimilarityScore() >= 0.95)
				.collect(Collectors.toList());
		if (!similarMethodNames.isEmpty()) {

			// check if the method with the given parameters already exists
			List<FunctionCallCandidate> functionCallCandidates = functionCallFinder.findFunctionCallCandidates(similarMethodNames,
					functionParameterMatches);
			List<FunctionCallCandidate> scoredCandidates = functionCallScorer.calculateCombinedScores(functionCallCandidates);
			if (scoredCandidates.stream().anyMatch(candidate -> candidate.getFunctionCallScore() >= 0.9)) {
				FunctionCallCandidate existingMethod = scoredCandidates.stream()
						.filter(candidate -> candidate.getFunctionCallScore() >= 0.9).collect(Collectors.toList()).get(0);

				methodName.insert(0, "NAMECONFLICT_");
				logger.error(
						"Conflict with the extracted method name: it already exists in the ontology in function '{}' with the given parameters. Added prefix 'NAMECONFLICT_'",
						existingMethod.toString());
			}
		}
	}

	/**
	 * Generates a method signature consisting of function name (verb) and appended
	 * parameters. Only those parameters who could not be perfectly matched to
	 * existing ontology individuals are appended as suffix to the generated method
	 * name.
	 *
	 * @param dclI
	 *            instruction
	 * @param methodNameBuilder
	 *            current method name
	 * @param functionParameterMatches
	 *            string matched parameters to the ontology
	 * @return method signature
	 */
	private ImmutablePair<String, List<FunctionParameterCandidate>> createMethodNameAndParameters(DeclarationInstruction dclI,
			StringBuilder methodNameBuilder, List<List<FunctionParameterCandidate>> functionParameterMatches) {
		StringBuilder tempName = new StringBuilder(" ");
		logger.debug("Synthesize method name of instruction name '{}'.", methodNameBuilder.toString());

		List<FunctionParameterCandidate> mappedParameters = new ArrayList<>();
		for (List<FunctionParameterCandidate> paramsPerChunk : functionParameterMatches) {
			if (paramsPerChunk.stream().anyMatch(candidate -> candidate.getSimilarityScore() >= 0.95)) {
				mappedParameters.addAll(
						paramsPerChunk.stream().filter(candidate -> candidate.getSimilarityScore() >= 0.95).collect(Collectors.toList())); // add perfect matched parameter to method signature
			}
		}

		// concat all instruction parameter names to the function name
		if (dclI.getClearedInstructionParameters() != null) {
			for (DeclarationParameter instructionParameter : dclI.getClearedInstructionParameters()) {
				String parameterName = instructionParameter.getClearedNominalizedParameterName();
				if (parameterName == null) {
					continue; // name got cleared from stopwords
				}

				tempName.append(" ").append(parameterName);
			}
			logger.debug("Appended cleared instruction parameters to function name: '{}'.", tempName.toString());

		} else {
			for (DeclarationParameter instructionParameter : dclI.getInstructionParameters()) {
				String parameterName = instructionParameter.getParameterName();

				tempName.append(" ").append(parameterName);
			}
			logger.debug("Appended instruction parameters to function name: '{}'.", tempName.toString());
		}

		// clear method name from stopwords  (e.g. setTheTable OK but setHowToTheTable not)
		String clearedMethodName = nameMapper.removeStopwordsFromString(tempName.toString());

		// ensure CamelCase between each word of a parameter and each parameter
		clearedMethodName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, clearedMethodName.replace(" ", "_")).replaceAll(" ", "");

		logger.debug("Removed stopwords and CamelCase synthesized function name: '{}'.", clearedMethodName);

		return new ImmutablePair<>(methodNameBuilder.append(clearedMethodName).toString(), mappedParameters);
	}

}
