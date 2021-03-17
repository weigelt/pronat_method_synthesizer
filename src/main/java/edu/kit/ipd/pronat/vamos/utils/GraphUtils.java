package edu.kit.ipd.pronat.vamos.utils;

import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public final class GraphUtils {

	private static IGraph graph = null;

	private static final Logger logger = LoggerFactory.getLogger(GraphUtils.class);
	public static final String SRL_ARC_TYPE = "srl";
	public static final String REFERENCE_ARC_TYPE = "reference";
	public static final String CONTEXT_RELATION_ARC_TYPE = "contextRelation";
	public static final String CONTEXT_ENTITY_NODE_TYPE = "contextEntity";
	public static final String CONTEXT_ACTION_NODETYPE = "contextAction";
	public static final String RELATION_ARC_TYPE = "relation";
	public static final String VALUE_ATTRIBUTE_NAME = "value";
	public static final String POSITION_ATTRIBUTE_NAME = "position";

	//	public GraphUtils(IGraph graph) {
	//		GraphUtils.graph = graph;
	//	}

	public static void setGraph(IGraph graph) {
		GraphUtils.graph = graph;
	}

	private static boolean graphIsSet() {
		return graph != null;
	}

	public static String getUtteranceString(List<INode> utteranceNodes) {
		StringJoiner utteranceString = new StringJoiner(" ");

		for (INode node : utteranceNodes) {
			String token = (String) node.getAttributeValue(VALUE_ATTRIBUTE_NAME);
			utteranceString.add(token);
		}

		return utteranceString.toString();
	}

	public static List<INode> getNodesOfUtterance() throws MissingDataException {
		if (!graphIsSet()) {
			logger.error("Graph not set!");
			throw new MissingDataException("Graph not set!");
		}
		ArrayList<INode> result = new ArrayList<>();
		IArcType nextArcType;
		if ((nextArcType = graph.getArcType(RELATION_ARC_TYPE)) != null) {
			if (graph instanceof ParseGraph) {
				ParseGraph pGraph = (ParseGraph) graph;
				INode current = pGraph.getFirstUtteranceNode();
				List<? extends IArc> outgoingNextArcs = current.getOutgoingArcsOfType(nextArcType);
				boolean hasNext = !outgoingNextArcs.isEmpty();
				result.add(current);
				while (hasNext) {
					//assume that only one NEXT arc exists
					if (outgoingNextArcs.size() == 1) {
						current = outgoingNextArcs.toArray(new IArc[outgoingNextArcs.size()])[0].getTargetNode();
						result.add(current);
						outgoingNextArcs = current.getOutgoingArcsOfType(nextArcType);
						hasNext = !outgoingNextArcs.isEmpty();
					} else {
						logger.error("Nodes have more than one NEXT Arc");
						throw new IllegalArgumentException("Nodes have more than one NEXT Arc");
					}
				}
			} else {
				logger.error("Graph is no ParseGraph!");
				throw new MissingDataException("Graph is no ParseGraph!");
			}
		} else {
			logger.error("Next Arctype does not exist!");
			throw new MissingDataException("Next Arctype does not exist!");
		}
		return result;
	}

	public static List<INode> sortNodesOfUtterance(List<INode> utteranceNodes) {
		utteranceNodes.sort(new Comparator<INode>() {
			@Override
			public int compare(INode o1, INode o2) {
				int posO1 = (int) o1.getAttributeValue(POSITION_ATTRIBUTE_NAME);
				int posO2 = (int) o2.getAttributeValue(POSITION_ATTRIBUTE_NAME);
				if (posO1 == posO2) {
					logger.error("These two nodes are the same! {}, {}", o1.getAttributeValue(VALUE_ATTRIBUTE_NAME),
							o2.getAttributeValue(VALUE_ATTRIBUTE_NAME));
					return 0;
				}
				return posO1 < posO2 ? -1 : 1;
			}
		});

		return utteranceNodes;
	}

	public static List<String> getListFromArrayToString(String representation) {
		List<String> result = new ArrayList<>();
		if (representation != null && !representation.equals("[]")) {
			result = Arrays.asList(representation.substring(1, representation.length() - 1).split(", "));
		}
		return result;
	}

	public static IArcType getSrlArcType() {
		if (!graphIsSet()) {
			logger.error("Graph not set! Returning null");
			return null;
		}
		return graph.getArcType(SRL_ARC_TYPE);
	}

	public static IArcType getReferenceArcType() {
		if (!graphIsSet()) {
			logger.error("Graph not set! Returning null");
			return null;
		}
		return graph.getArcType(REFERENCE_ARC_TYPE);
	}

	public static IArcType getContextRelationArcType() {
		if (!graphIsSet()) {
			logger.error("Graph not set! Returning null");
			return null;
		}
		return graph.getArcType(CONTEXT_RELATION_ARC_TYPE);
	}

	public static INodeType getContextEntityNodeType() {
		if (!graphIsSet()) {
			logger.error("Graph not set! Returning null");
			return null;
		}
		return graph.getNodeType(CONTEXT_ENTITY_NODE_TYPE);
	}

	public static INodeType getContextActionNodeType() {
		if (!graphIsSet()) {
			logger.error("Graph not set! Returning null");
			return null;
		}
		return graph.getNodeType(CONTEXT_ACTION_NODETYPE);
	}
}