package edu.kit.ipd.parse.vamos.utils;

import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.graph.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class GraphUtils {
	private static IGraph graph;

	private static final Logger logger = LoggerFactory.getLogger(GraphUtils.class);

	public GraphUtils(IGraph graph) {
		GraphUtils.graph = graph;
	}

	public static String getUtteranceString(List<INode> utteranceNodes) {
		StringJoiner utteranceString = new StringJoiner(" ");

		for (INode node : utteranceNodes) {
			String token = (String) node.getAttributeValue("value");
			utteranceString.add(token);
		}

		return utteranceString.toString();
	}

	public static List<INode> getNodesOfUtterance() throws MissingDataException {
		ArrayList<INode> result = new ArrayList<>();
		IArcType nextArcType;
		if ((nextArcType = graph.getArcType("relation")) != null) {
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
				int posO1 = (int) o1.getAttributeValue("position");
				int posO2 = (int) o2.getAttributeValue("position");
				if (posO1 == posO2) {
					logger.error("These two nodes are the same! {}, {}", o1.getAttributeValue("value"), o2.getAttributeValue("value"));
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
		return graph.getArcType("srl");
	}

	public static IArcType getReferenceArcType() {
		return graph.getArcType("reference");
	}

	public static IArcType getContextRelationArcType() {
		return graph.getArcType("contextRelation");
	}

	public static INodeType getContextEntityNodeType() {
		return graph.getNodeType("contextEntity");
	}

	public static INodeType getContextActionNodeType() {
		return graph.getNodeType("contextAction");
	}

	public static INode getPrevNode(INode node) {
		List<? extends IArc> arcList = node.getIncomingArcsOfType(graph.getArcType("relation"));
		return arcList.size() == 1 ? arcList.get(0).getSourceNode() : null;
	}
}
