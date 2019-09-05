package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.luna.agent.AbstractAgent;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.IArcType;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.luna.graph.INodeType;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.vamos.command_classification.BinaryNeuralClassifier;
import edu.kit.ipd.parse.vamos.command_classification.MulticlassNeuralClassifier;
import edu.kit.ipd.parse.vamos.command_classification.SrlExtractor;
import edu.kit.ipd.parse.vamos.command_representation.AbstractCommand;
import edu.kit.ipd.parse.vamos.command_classification.MulticlassLabels;
import edu.kit.ipd.parse.vamos.ontology_mapping.OntologyMapper;
import edu.kit.ipd.parse.vamos.programm_representation.CommandCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionCallCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;
import edu.kit.ipd.parse.vamos.programm_representation.MethodSignatureCandidate;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import org.kohsuke.MetaInfServices;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * @author Vanessa Steurer
 *
 */

@MetaInfServices(AbstractAgent.class)
public class MethodSynthesizer extends AbstractAgent {

	private static final String ID = "commandFinder";
	private static final Logger logger = LoggerFactory.getLogger(MethodSynthesizer.class);
	private static final Properties props =  ConfigManager.getConfiguration(MethodSynthesizer.class);

	private static final String IS_TEACHING_SEQUENCE = "isTeachingSequence";		// part1 (classification agent)
	private static final String IS_TEACHING_SEQUENCE_PROB = "isTeachingSequenceProbability";
	private static final String TEACHING_SEQUENCE_PART = "teachingSequencePart";

	// to represent command nodes
	private static final String NODE_TYPE_COMMAND_MAPPER = "commandMapper";						// part3 (mapping agent)
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


    private List<INode> utteranceNodes;
	private BinaryNeuralClassifier binClf;
	private MulticlassNeuralClassifier mclassClf;
	private boolean useContext = true;

    /*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.LunaObserver#init()
	 */
	@Override
	public void init() {
		setId(ID);
		System.out.println(" ");
		logger.info("************* Start execution of Methodsynthesizer Agent. *************");

		new GraphUtils(graph);
		utteranceNodes = new ArrayList<>();
		try {
			utteranceNodes = GraphUtils.getNodesOfUtterance();
		} catch (MissingDataException e) {
			e.printStackTrace();
			logger.error("No valid ParseGraph. Abort Agent execution.", e);
		}

		binClf = new BinaryNeuralClassifier(props);
		mclassClf = new MulticlassNeuralClassifier(props);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see edu.kit.ipd.parse.luna.agent.AbstractAgent#exec()
	 */
	@Override
	public void exec() {
		// check if graph contains SRL-Labels, if not, exit
		if (graph.getArcsOfType(graph.getArcType("srl")).isEmpty()) {
			try {
				throw new MissingDataException();
			} catch (MissingDataException e) {
				logger.error("No SRL-Annotations found.");
			}
		}

		// check if graph contains context-Labels for synonyms and corefs
		if (graph.getArcsOfType(graph.getArcType("reference")).isEmpty()
				|| graph.getArcsOfType(graph.getArcType("contextRelation")).isEmpty()) {
			useContext = false;
			logger.error("No Context-Annotations found. No usage of synonyms and coref resolution for string matching.");
		}

		String utterance = GraphUtils.getUtteranceString(utteranceNodes);
		System.out.println(" ");
        logger.info("input sentence: {}", utterance);

		// binary classification: TeachingSequence yes/no
		float[] binaryPrediction = getBinaryClfIsTeachingSequenceResult(utterance);
		boolean isTeachingSequence = binClf.isTeachingSequence(binaryPrediction);

        // mclass classification: methodbody/methodhead
		List<MulticlassLabels> mclassLabels = getMclassClfTeachingSequencePartsResult(utterance);

		saveToGraph(isTeachingSequence, binaryPrediction, mclassLabels);

		long declarationLabels = mclassLabels.stream().filter(l -> l.equals(MulticlassLabels.DECL)).count();
		if (!isTeachingSequence && ((binaryPrediction[0] > 0.1f && declarationLabels > 2) || declarationLabels > 5)) {
			isTeachingSequence = true;
			logger.info("Binary classifier detected no teaching sequence (<0.5) BUT multiclass classifier " +
					"contains {} declaration labels -> interpreting as Teaching Command.", declarationLabels);
		}

		// merge classification results with semantic role labels: methodname, params
		SrlExtractor srl = new SrlExtractor();
        AbstractCommand command = mergeClfResults(srl, isTeachingSequence, mclassLabels);

		OntologyMapper mapper = new OntologyMapper(useContext);
		CommandCandidate commandMappingToAPI = mapper.findCommandMappingToAPI(command);
		logger.debug("Mapped command: \n{}", commandMappingToAPI.toString());

		saveToGraph(commandMappingToAPI);
	}


	private float[] getBinaryClfIsTeachingSequenceResult(String utterance) {
        INDArray binResult = binClf.getSinglePrediction(utterance, binClf.getModel());
		float[] predictedClasses = binClf.getPredictedClasses(binResult, 0);

		logger.info("Found teaching sequence prediction: {}.", predictedClasses);
        return predictedClasses;
    }

    protected List<MulticlassLabels> getMclassClfTeachingSequencePartsResult(String utterance) {
        List<MulticlassLabels> mclassLabels;
		boolean useInternalModel = Boolean.valueOf(props.getProperty("USE_INTERAL_MCLASS_MODEL"));
		logger.info("Read in configuration for USE_INTERAL_MCLASS_MODEL:{}.", useInternalModel);

		if (useInternalModel) { // load model from dl4j
            INDArray mclassPrediction = mclassClf.getSinglePrediction(utterance, mclassClf.getModel());
            mclassLabels = mclassClf.getInterpretedPredictedLabels(mclassPrediction, utteranceNodes.size());

        } else {  // use keras external MclassModel by calling python script
			INDArray mclassPrediction = mclassClf.getExternalMclassModelSinglePrediction(mclassClf.tokenizeSingleInput(utterance), utteranceNodes.size());
			mclassLabels = mclassClf.getInterpretedPredictedLabels(mclassPrediction, utteranceNodes.size());
        }

        logger.info("Found different parts of teaching sequence with multiclass clf.");
        return mclassLabels;
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

	private void saveToGraph(boolean binaryIsTeachingSequence, float[] isTeachingSequenceProbability, List<MulticlassLabels> mclassLabels) {
		if (!graph.getNodeType("token").containsAttribute(IS_TEACHING_SEQUENCE, "String")) {
			graph.getNodeType("token").addAttributeToType("String", IS_TEACHING_SEQUENCE);
		}

		if (!graph.getNodeType("token").containsAttribute(IS_TEACHING_SEQUENCE_PROB, "float")) {
			graph.getNodeType("token").addAttributeToType("float", IS_TEACHING_SEQUENCE_PROB);
		}

		if (!graph.getNodeType("token").containsAttribute(TEACHING_SEQUENCE_PART, "String")) {
			graph.getNodeType("token").addAttributeToType("String", TEACHING_SEQUENCE_PART);
		}

		for (int i = 0; i < utteranceNodes.size(); i++) {
			INode currentNode = utteranceNodes.get(i);
			currentNode.setAttributeValue(IS_TEACHING_SEQUENCE, binaryIsTeachingSequence);
			// first index of float-Array equals probability for binary-TeachingSequence-class
			currentNode.setAttributeValue(IS_TEACHING_SEQUENCE_PROB, isTeachingSequenceProbability[0]);
			currentNode.setAttributeValue(TEACHING_SEQUENCE_PART, mclassLabels.get(i));
		}
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
		if (commandDeclarationType == null) commandDeclarationType = graph.createNodeType(NODE_TYPE_COMMAND_DECL);

		INodeType commandDescriptionType = graph.getNodeType(NODE_TYPE_COMMAND_DESC);
		if (commandDescriptionType == null) commandDescriptionType = graph.createNodeType(NODE_TYPE_COMMAND_DESC);

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
		if (commandMapperArcType == null) commandMapperArcType = graph.createArcType(ARC_TYPE_COMMAND_MAPPER);

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
				graph.createArc(functionNameNode, (INode) tokenNode, commandMapperArcType);	// arc to token node
			}

			for (FunctionParameterCandidate param : methodSignature.getParameters()) {
				INode functionParamNode = graph.createNode(functionParameterType);
				functionParamNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_PARAMETER,  param.isPrimitiveType() ?
						param.getExtractedParameter().getParameterName() : param.getParameterCandidate().getFullName());
				graph.createArc(functionNameNode, functionParamNode, commandMapperArcType); // arc to parent node FunctionName

				for (INode tokenNode : param.getExtractedParameter().getClearedParameterNodes()) {
					graph.createArc(functionNameNode, tokenNode, commandMapperArcType); // arc to token node
				}
			}
		}

		// create DESCRIPTION INSTRUCTION (method body / script) NODES
		for (int i = 0; i < functionCallCandidates.size(); i++) {	// number of candidates
			INode functionCallNode = graph.createNode(functionCallType);
			functionCallNode.setAttributeValue(ATTRIBUTE_CALL_NUMBER, i + 1);
			graph.createArc(commandDescNode, functionCallNode, commandMapperArcType); // arc to parent node Desc

			List<FunctionCallCandidate> topNCandidates = functionCallCandidates.get(i);
			for (int j = 0; j < topNCandidates.size(); j++) {
				FunctionCallCandidate candidate = topNCandidates.get(j);

				INode functionNameNode = graph.createNode(functionNameType);
				functionNameNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_FUNCTION, candidate.getNameCandidate().getMethodCandidate().getFullName());
				functionNameNode.setAttributeValue(ATTRIBUTE_TOPN_CANDIDATE, j + 1);
				functionNameNode.setAttributeValue(ATTRIBUTE_TOPN_CANDIDATE_SCORE, candidate.getFunctionCallScore());
				graph.createArc(functionCallNode, functionNameNode, commandMapperArcType); // arc to parent node FunctionCall

				for (INode tokenNode : candidate.getNameCandidate().getExtractedInstruction().getClearedInstructionNameNodes()) {
					graph.createArc(functionNameNode, tokenNode, commandMapperArcType);	// arc to token node
				}

				for (FunctionParameterCandidate param : candidate.getParameterCandidates()) {
					INode functionParamNode = graph.createNode(functionParameterType);
					functionParamNode.setAttributeValue(ATTRIBUTE_ONTOLOGY_PARAMETER, param.isPrimitiveType() ?
								param.getExtractedParameter().getParameterName() : param.getParameterCandidate().getFullName());
					if (candidate.getMatchingOntologyParametersMap() != null && candidate.getMatchingOntologyParametersMap().get(param) != null) {
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