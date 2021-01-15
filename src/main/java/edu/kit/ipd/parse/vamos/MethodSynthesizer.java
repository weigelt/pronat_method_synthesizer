package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.luna.agent.AbstractAgent;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.IArcType;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.graph.INodeType;
import edu.kit.ipd.parse.luna.graph.ParseGraph;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.vamos.command_representation.AbstractCommand;
import edu.kit.ipd.parse.vamos.ontology_mapping.OntologyMapper;
import edu.kit.ipd.parse.vamos.programm_representation.CommandCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionCallCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.MethodSignatureCandidate;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 *
 */

@MetaInfServices(AbstractAgent.class)
public class MethodSynthesizer extends AbstractAgent {

	private static final String ID = "commandFinder";
	private static final Logger logger = LoggerFactory.getLogger(MethodSynthesizer.class);
	private static final Properties props = ConfigManager.getConfiguration(MethodSynthesizer.class);

	private static final String IS_TEACHING_SEQUENCE = "isTeachingSequence"; // part1 (classification agent)
	private static final String IS_TEACHING_SEQUENCE_PROB = "isTeachingSequenceProbability";
	private static final String TEACHING_SEQUENCE_PART = "teachingSequencePart";

	// to represent command nodes
	private static final String NODE_TYPE_COMMAND_MAPPER = "commandMapper"; // part3 (mapping agent)
	private static final String ATTRIBUTE_NAME_TEACHING_SEQUENCE = "isTeachingSequence";

	// to represent decl / desc nodes
	private static final String NODE_TYPE_COMMAND_DECL = "declaration";
	private static final String NODE_TYPE_COMMAND_DESC = "description";

	// to represent function call nodes
	private static final String NODE_TYPE_FUNCTION_CALL = "functionCall";
	private static final String ATTRIBUTE_CALL_NUMBER = "number";

	// to represent function name nodes
	private static final String NODE_TYPE_FUNCTION_NAME = "functionName";
	private static final String ATTRIBUTE_ONTOLOGY_FUNCTION = "ontologyMethod";
	private static final String ATTRIBUTE_TOPN_CANDIDATE = "topN";
	private static final String ATTRIBUTE_TOPN_CANDIDATE_SCORE = "topNScore";

	// to represent param nodes
	private static final String NODE_TYPE_FUNCTION_PARAMETER = "functionParameter";
	private static final String ATTRIBUTE_ONTOLOGY_PARAMETER = "ontologyParameter";
	private static final String ATTRIBUTE_ONTOLOGY_METHOD_PARAMETER_TO_MAP = "methodParameterToFill";

	private static final String ARC_TYPE_COMMAND_MAPPER = "commandMapper";

	public static final String NEXT_TOKEN_RELATION = "relation";
	public static final String TOKEN_NODE_TYPE = "token";
	public static final String SRL_ARC_TYPE = "srl";

	private List<INode> utteranceNodes;
	private boolean useContext = true;

	public MethodSynthesizer() {
		setId(ID);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.LunaObserver#init()
	 */
	@Override
	public void init() {
		//		logger.info("************* Start init of Method Synthesizer Agent. *************");
		//		setId(ID);
		//		logger.info("************ Finished init of Method Synthesizer Agent. ************");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.AbstractAgent#exec()
	 */
	@Override
	public void exec() {

		if (checkMandatory()) {

			// check if graph contains context-Labels for synonyms and corefs
			if (graph.getArcsOfType(graph.getArcType("reference")).isEmpty()
					|| graph.getArcsOfType(graph.getArcType("contextRelation")).isEmpty()) {
				useContext = false;
				logger.error("No Context-Annotations found. No usage of synonyms and coref resolution for string matching.");
			}

			GraphUtils.setGraph(graph);
			utteranceNodes = new ArrayList<>();
			try {
				utteranceNodes = GraphUtils.getNodesOfUtterance();
			} catch (MissingDataException e) {
				logger.error("Something went wrong while reading utterance nodes");
				e.printStackTrace();
			}
			boolean isTeachingSequence = Boolean.valueOf((String) utteranceNodes.get(0).getAttributeValue(IS_TEACHING_SEQUENCE));
			float binaryPrediction = (float) utteranceNodes.get(0).getAttributeValue(IS_TEACHING_SEQUENCE_PROB);

			// mclass classification: methodbody/methodhead
			List<MulticlassLabels> mclassLabels = null;

			try {
				mclassLabels = getMclassClfTeachingSequencePartsResult(utteranceNodes);
			} catch (IllegalArgumentException e) {
				logger.error(e.getMessage());
			}
			//		saveToGraph(isTeachingSequence, binaryPrediction, mclassLabels);

			long declarationLabels = mclassLabels.stream().filter(l -> l.equals(MulticlassLabels.DECL)).count();
			if (!isTeachingSequence && ((binaryPrediction > 0.1f && declarationLabels > 2) || declarationLabels > 5)) {
				isTeachingSequence = true;
				logger.info("Binary classifier detected no teaching sequence (<0.5) BUT multiclass classifier "
						+ "contains {} declaration labels -> interpreting as Teaching Command.", declarationLabels);
			}

			// merge classification results with semantic role labels: methodname, params
			SrlExtractor srl = new SrlExtractor();
			AbstractCommand command = mergeClfResults(srl, isTeachingSequence, mclassLabels);

			OntologyMapper mapper = new OntologyMapper(useContext);
			CommandCandidate commandMappingToAPI = mapper.findCommandMappingToAPI(command);
			logger.debug("Mapped command: \n{}", commandMappingToAPI.toString());

			saveToGraph(commandMappingToAPI);
		}
	}

	private boolean checkMandatory() {

		if (!(graph instanceof ParseGraph)) {
			logger.error("Graph is not an instance of ParseGraph, aborting!");
			return false;
		} else if (!graph.hasArcType(NEXT_TOKEN_RELATION) || !graph.hasNodeType(TOKEN_NODE_TYPE)
				|| graph.getNodesOfType(graph.getNodeType(TOKEN_NODE_TYPE)).isEmpty()) {
			logger.info("Graph has no utterance nodes or next edges, aborting!");
			return false;
		} else if (!graph.hasArcType(SRL_ARC_TYPE) || graph.getArcsOfType(graph.getArcType(SRL_ARC_TYPE)).isEmpty()) {
			logger.info("No SRL-Annotations found, aborting!");
			return false;
		} else if (!graph.getNodeType(TOKEN_NODE_TYPE).containsAttribute(IS_TEACHING_SEQUENCE, "String")
				|| !graph.getNodeType(TOKEN_NODE_TYPE).containsAttribute(IS_TEACHING_SEQUENCE_PROB, "float")
				|| !graph.getNodeType(TOKEN_NODE_TYPE).containsAttribute(TEACHING_SEQUENCE_PART, "String")) {
			logger.info("No teaching detector information so far, waiting for next iteration");
			return false;
		} else {
			return true;
		}
	}

	protected List<MulticlassLabels> getMclassClfTeachingSequencePartsResult(List<INode> utteranceNodes) throws IllegalArgumentException {
		List<MulticlassLabels> resultList = new ArrayList<>();
		for (INode node : utteranceNodes) {
			String labelString = (String) node.getAttributeValue(TEACHING_SEQUENCE_PART);
			if (labelString.equals("DECL") || labelString.equals("DESC") || labelString.equals("ELSE")) {
				resultList.add(MulticlassLabels.valueOf(labelString));
			} else {
				throw new IllegalArgumentException("Unexpected multi class label: " + labelString);
			}
		}
		return resultList;
	}

	private AbstractCommand mergeClfResults(SrlExtractor srl, boolean isTeachingSequence, List<MulticlassLabels> mclassLabels) {
		CommandBuilder tsBuilder = new CommandBuilder(srl, utteranceNodes, mclassLabels);

		AbstractCommand command;
		if (isTeachingSequence) {
			command = tsBuilder.buildTeachingCommand();
		} else {
			command = tsBuilder.buildExecutionCommand();
		}

		logger.debug(command.toString());
		return command;
	}

	private void saveToGraph(CommandCandidate commandMapping) {
		MethodSignatureCandidate methodSignature = commandMapping.getMethodSignature();
		boolean isTeachingSequence = methodSignature != null;
		List<List<FunctionCallCandidate>> functionCallCandidates = commandMapping.getFunctionCallCandidates();

		INodeType commandMapperType = graph.getNodeType(NODE_TYPE_COMMAND_MAPPER);
		if (commandMapperType == null) {
			commandMapperType = graph.createNodeType(NODE_TYPE_COMMAND_MAPPER);
			commandMapperType.addAttributeToType("boolean", ATTRIBUTE_NAME_TEACHING_SEQUENCE);
		}

		INodeType commandDeclarationType = graph.getNodeType(NODE_TYPE_COMMAND_DECL);
		if (commandDeclarationType == null) {
			commandDeclarationType = graph.createNodeType(NODE_TYPE_COMMAND_DECL);
		}

		INodeType commandDescriptionType = graph.getNodeType(NODE_TYPE_COMMAND_DESC);
		if (commandDescriptionType == null) {
			commandDescriptionType = graph.createNodeType(NODE_TYPE_COMMAND_DESC);
		}

		INodeType functionCallType = graph.getNodeType(NODE_TYPE_FUNCTION_CALL);
		if (functionCallType == null) {
			functionCallType = graph.createNodeType(NODE_TYPE_FUNCTION_CALL);
			functionCallType.addAttributeToType("int", ATTRIBUTE_CALL_NUMBER);
		}

		INodeType functionNameType = graph.getNodeType(NODE_TYPE_FUNCTION_NAME);
		if (functionNameType == null) {
			functionNameType = graph.createNodeType(NODE_TYPE_FUNCTION_NAME);
			functionNameType.addAttributeToType("String", ATTRIBUTE_ONTOLOGY_FUNCTION);
			functionNameType.addAttributeToType("int", ATTRIBUTE_TOPN_CANDIDATE);
			functionNameType.addAttributeToType("double", ATTRIBUTE_TOPN_CANDIDATE_SCORE);
		}

		INodeType functionParameterType = graph.getNodeType(NODE_TYPE_FUNCTION_PARAMETER);
		if (functionParameterType == null) {
			functionParameterType = graph.createNodeType(NODE_TYPE_FUNCTION_PARAMETER);
			functionParameterType.addAttributeToType("String", ATTRIBUTE_ONTOLOGY_PARAMETER);
			functionParameterType.addAttributeToType("String", ATTRIBUTE_ONTOLOGY_METHOD_PARAMETER_TO_MAP);
		}

		IArcType commandMapperArcType = graph.getArcType(ARC_TYPE_COMMAND_MAPPER);
		if (commandMapperArcType == null) {
			commandMapperArcType = graph.createArcType(ARC_TYPE_COMMAND_MAPPER);
		}

		INode commandMapperNode = graph.createNode(commandMapperType);
		commandMapperNode.setAttributeValue(ATTRIBUTE_NAME_TEACHING_SEQUENCE, isTeachingSequence);

		INode commandDeclNode = null;
		if (isTeachingSequence) {
			commandDeclNode = graph.createNode(commandDeclarationType);
			graph.createArc(commandMapperNode, commandDeclNode, commandMapperArcType); // arc to parent node commandMapper
		}

		INode commandDescNode = graph.createNode(commandDescriptionType);
		graph.createArc(commandMapperNode, commandDescNode, commandMapperArcType); // arc to parent node commandMapper

		// create DECLARATION INSTRUCTION (method signature) NODES
		if (isTeachingSequence) {
			INode functionCallNode = graph.createNode(functionCallType);
			functionCallNode.setAttributeValue(ATTRIBUTE_CALL_NUMBER, 1);
			graph.createArc(commandDeclNode, functionCallNode, commandMapperArcType); // arc to parent node Decl

			INode functionNameNode = graph.createNode(functionNameType);
			functionNameNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_FUNCTION, methodSignature.getMethodName());
			functionNameNode.setAttributeValue(ATTRIBUTE_TOPN_CANDIDATE, 1);
			graph.createArc(functionCallNode, functionNameNode, commandMapperArcType); // arc to parent node FunctionCall

			for (Object tokenNode : methodSignature.getInstruction().getInstructionNameNodes()) {
				graph.createArc(functionNameNode, (INode) tokenNode, commandMapperArcType); // arc to token node
			}

			for (FunctionParameterCandidate param : methodSignature.getParameters()) {
				INode functionParamNode = graph.createNode(functionParameterType);
				functionParamNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_PARAMETER,
						param.isPrimitiveType() ? param.getExtractedParameter().getParameterName()
								: param.getParameterCandidate().getFullName());
				graph.createArc(functionNameNode, functionParamNode, commandMapperArcType); // arc to parent node FunctionName

				for (INode tokenNode : param.getExtractedParameter().getClearedParameterNodes()) {
					graph.createArc(functionNameNode, tokenNode, commandMapperArcType); // arc to token node
				}
			}
		}

		// create DESCRIPTION INSTRUCTION (method body / script) NODES
		for (int i = 0; i < functionCallCandidates.size(); i++) { // number of candidates
			INode functionCallNode = graph.createNode(functionCallType);
			functionCallNode.setAttributeValue(ATTRIBUTE_CALL_NUMBER, i + 1);
			graph.createArc(commandDescNode, functionCallNode, commandMapperArcType); // arc to parent node Desc

			List<FunctionCallCandidate> topNCandidates = functionCallCandidates.get(i);
			for (int j = 0; j < topNCandidates.size(); j++) {
				FunctionCallCandidate candidate = topNCandidates.get(j);

				INode functionNameNode = graph.createNode(functionNameType);
				functionNameNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_FUNCTION,
						candidate.getNameCandidate().getMethodCandidate().getFullName());
				functionNameNode.setAttributeValue(ATTRIBUTE_TOPN_CANDIDATE, j + 1);
				functionNameNode.setAttributeValue(ATTRIBUTE_TOPN_CANDIDATE_SCORE, candidate.getFunctionCallScore());
				graph.createArc(functionCallNode, functionNameNode, commandMapperArcType); // arc to parent node FunctionCall

				for (INode tokenNode : candidate.getNameCandidate().getExtractedInstruction().getClearedInstructionNameNodes()) {
					graph.createArc(functionNameNode, tokenNode, commandMapperArcType); // arc to token node
				}

				for (FunctionParameterCandidate param : candidate.getParameterCandidates()) {
					INode functionParamNode = graph.createNode(functionParameterType);
					functionParamNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_PARAMETER,
							param.isPrimitiveType() ? param.getExtractedParameter().getParameterName()
									: param.getParameterCandidate().getFullName());
					if (candidate.getMatchingOntologyParametersMap() != null
							&& candidate.getMatchingOntologyParametersMap().get(param) != null) {
						functionParamNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_METHOD_PARAMETER_TO_MAP,
								candidate.getMatchingOntologyParametersMap().get(param).getFullName());
					}
					graph.createArc(functionNameNode, functionParamNode, commandMapperArcType); // arc to parent node FunctionName

					for (INode tokenNode : param.getExtractedParameter().getClearedParameterNodes()) {
						graph.createArc(functionNameNode, tokenNode, commandMapperArcType); // arc to token node
					}
				}
			}
		}
	}
}