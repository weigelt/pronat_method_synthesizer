package edu.kit.ipd.parse.vamos.command_classification;

import edu.kit.ipd.parse.luna.graph.IArc;
import edu.kit.ipd.parse.luna.graph.IArcType;
import edu.kit.ipd.parse.luna.graph.INode;
import edu.kit.ipd.parse.vamos.utils.GraphUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class SrlExtractor {
	private static final Logger logger = LoggerFactory.getLogger(SrlExtractor.class);

	private static IArcType srlType = GraphUtils.getSrlArcType();

	private static final String VRole = "V";
	private static final String ModalSrlRole = "AM-MOD";
	private static final String nextRelation = "next";

	private boolean useToBeVerbModifier;

	public SrlExtractor(boolean useToBeVerbModifier) {
		this.useToBeVerbModifier = useToBeVerbModifier;
	}

	/**
	 * Get V-SRL-nodes of each chunk. Could consist of main V-SRL and modifier V-SRL
	 * nodes ("need to go", "turn to face").
	 * 
	 * @param nodes
	 *            of utterance
	 * @return Map of main V-SRL-node with itself + possible modifiers in List;
	 *         Main-V-SRL-node is always first in list!
	 */
	public Map<INode, List<INode>> getMainAndModifierVSrlNodes(List<INode> nodes) {
		Map<INode, List<INode>> vNodesWithModifiers = new HashMap<>();
		Map<Integer, INode> vNodesPerInstruction = new HashMap<>();
		List<INode> vSrlNodes = findVSrlNodes(nodes);

		for (INode node : vSrlNodes) {
			int instructionNumber = (int) node.getAttributeValue("instructionNumber");
			if (vNodesPerInstruction.containsKey(instructionNumber)) {
				logger.debug("Found two SRL-V nodes with same instruction number.");
				INode mainVNode = getMainVNode(node, vNodesPerInstruction.get(instructionNumber));
				INode modifyingVNode = mainVNode.equals(node) ? vNodesPerInstruction.get(instructionNumber) : node;
				vNodesPerInstruction.put(instructionNumber, mainVNode); // overwrite to main node

				if (vNodesWithModifiers.get(mainVNode) != null) {
					List<INode> list = vNodesWithModifiers.get(mainVNode);
					list.add(modifyingVNode); // append modifying SRL-Vs -> main V-SRL is always first
					vNodesWithModifiers.put(mainVNode, list);
				} else {
					List<INode> vnodes = new ArrayList<>();
					vnodes.add(mainVNode);
					vnodes.add(modifyingVNode);
					vNodesWithModifiers.put(mainVNode, vnodes);
					vNodesWithModifiers.remove(modifyingVNode); // remove modifyer V-SRL from as key from main-V-SRL-Map
				}

			} else {
				vNodesPerInstruction.put(instructionNumber, node); // add node
				vNodesWithModifiers.put(node, new ArrayList<>(List.of(node)));
			}
			if (useToBeVerbModifier) {
				addToBeModifiers(vNodesWithModifiers);
			}
		}

		return vNodesWithModifiers;
	}

	private void addToBeModifiers(Map<INode, List<INode>> vNodesWithModifiers) {
		for (INode node : vNodesWithModifiers.keySet()) {
			INode toBe = extractToBes(node);
			if (toBe != null && !vNodesWithModifiers.get(node).contains(toBe)) {
				vNodesWithModifiers.get(node).add(toBe);
			}
		}
	}

	private INode extractToBes(INode node) {

		INode extractedToBe = null;

		INode pred = GraphUtils.getPrevNode(node);
		INode act = node;

		while (pred != null && pred.getAttributeValue("chunkName").equals("VP") && !act.getAttributeValue("chunkIOB").equals("B-VP")
				&& pred.getAttributeValue("instructionNumber").equals(act.getAttributeValue("instructionNumber"))) {
			if (pred.getAttributeValue("lemma").equals("be")) {
				extractedToBe = pred;
				break;
			} else {
				act = pred;
				pred = GraphUtils.getPrevNode(act);
			}
		}
		return extractedToBe;
	}

	/**
	 * Find corresponding SRL nodes to each main verb of the utterance.
	 * 
	 * @param srlMainVNodes
	 *            main V-SRL nodes (from getMainAndModifierVSrlNodes.keySet()!)
	 * @return List of chunks (List<INode>) for each main V-SRL-node
	 */
	public List<List<INode>> getSrlChunksFromMainVSrlNodes(List<INode> srlMainVNodes) {
		List<List<INode>> srlChunks = new ArrayList<>();

		for (INode node : srlMainVNodes) {
			Set<INode> srlChunkSet = new HashSet<>();
			srlChunkSet.add(node); // VRole node itself

			srlChunkSet.addAll(findConnectedSrlNodes(node, srlChunkSet));

			List<INode> sortedSrlChunkSet = GraphUtils.sortNodesOfUtterance(new ArrayList<>(srlChunkSet));
			logger.debug("Got SRL-chunks of SRL-V-token '{}': {}", node.getAttributeValue("value"),
					GraphUtils.getUtteranceString(sortedSrlChunkSet));
			srlChunks.add(sortedSrlChunkSet);
		}

		return srlChunks;
	}

	/**
	 * Find all V-SRL-nodes in utterance.
	 * 
	 * @param nodes
	 *            utterance nodes
	 * @return V-SRL-nodes
	 */
	private List<INode> findVSrlNodes(List<INode> nodes) {
		List<INode> vSrlNodes = new ArrayList<>();

		for (INode node : nodes) {
			List<? extends IArc> srlIncomingArcs = node.getIncomingArcsOfType(srlType);
			if (srlIncomingArcs.stream().anyMatch(arc -> arc.getAttributeValue("role").equals(VRole))) {

				// only add verbs with PoS == VB*
				if (!node.getAttributeValue("pos").toString().startsWith("VB")) {
					logger.debug("Found SRL-V token '{}' which is no part of speech VB. Skip. it.", node.getAttributeValue("value"));
					continue;
				}

				// only add verbs with chunk == VP
				if (!node.getAttributeValue("chunkName").toString().equals("VP")) {
					logger.debug("Found SRL-V token '{}' which is no VP chunk. Skip. it.", node.getAttributeValue("value"));
					continue;
				}

				// skip modal verbs
				if (srlIncomingArcs.stream().anyMatch(arc -> arc.getAttributeValue("role").equals(ModalSrlRole))
						|| node.getAttributeValue("pos").equals("MD")) {
					logger.debug("Found SRL-V modal verb '{}'. Skip. it.", node.getAttributeValue("value"));
					continue;
				}

				vSrlNodes.add(node);
			}
		}
		logger.debug("Found SRL-V-tokens: {}", GraphUtils.getUtteranceString(vSrlNodes));
		return vSrlNodes;
	}

	/**
	 * For two V-SRL-nodes: check which one is the main verb. Default the second one
	 * 
	 * @param vNode1
	 *            first node
	 * @param vNode2
	 *            second node
	 * @return main V-SRL-node
	 */
	private INode getMainVNode(INode vNode1, INode vNode2) {
		List<? extends IArc> srlIncomingArcsN1 = vNode1.getIncomingArcsOfType(srlType);
		List<? extends IArc> srlIncomingArcsN2 = vNode2.getIncomingArcsOfType(srlType);
		INode mainVerbNode;

		// main verbs DO have modifying SRL-Arcs by the second (modal) verb
		if (srlIncomingArcsN1.stream().anyMatch(arc -> !arc.getAttributeValue("role").equals(VRole))) {
			mainVerbNode = vNode1;

		} else if (srlIncomingArcsN2.stream().anyMatch(arc -> !arc.getAttributeValue("role").equals(VRole))) {
			mainVerbNode = vNode2;

		} else { // return the second verb hardcoded (it is most likely the main verb)
			mainVerbNode = (int) vNode1.getAttributeValue("position") > (int) vNode2.getAttributeValue("position") ? vNode1 : vNode2;
		}

		logger.debug("Found main SRL-V node {}. Skip the modifying/ first SRL-V node {}.", vNode1.getAttributeValue("value"),
				vNode2.getAttributeValue("value"));
		return mainVerbNode;
	}

	/**
	 * Recursively find annotated SRL-nodes: Follow OUTgoing-SRL-arcs from SRL-V
	 * node.
	 * 
	 * @param node
	 *            node to follow its SRL-arcs
	 * @param srlChunkSet
	 *            connected SRL-nodes
	 * @return set of SRL-chunks
	 */
	private Set<INode> findConnectedSrlNodes(INode node, Set<INode> srlChunkSet) {
		List<? extends IArc> srlArcs = node.getOutgoingArcsOfType(srlType);
		//        logger.debug("New recursive loop. Found SRL-tokens: {}", GraphUtils.getUtteranceString(new ArrayList<>(srlChunkSet)));

		for (IArc arc : srlArcs) {
			if (!arc.getAttributeValue("role").equals(VRole)) {
				INode nextSrlNode = arc.getTargetNode();

				if (srlChunkSet.contains(nextSrlNode)) {
					return srlChunkSet;
				}

				srlChunkSet.add(nextSrlNode);
				Set<INode> recursiveNodes = findConnectedSrlNodes(nextSrlNode, srlChunkSet); // recursive implementation
				srlChunkSet.addAll(recursiveNodes);
			}
		}

		return srlChunkSet;
	}

	/**
	 * Find annotated SRL-nodes: Follow INcoming-SRL-arcs from SRL-V-nodes
	 * 
	 * @param nodes
	 *            list to check
	 * @return list of SRL-Arg-node chunks
	 */
	public List<List<INode>> findParameterNodes(List<INode> nodes) {
		Map<String, List<INode>> roles = new HashMap<>();

		for (INode node : nodes) {
			List<? extends IArc> srlArcs = node.getIncomingArcsOfType(srlType);
			if (!srlArcs.isEmpty()) {

				// skip (modal) verbs
				if (srlArcs.stream().anyMatch(arc -> arc.getAttributeValue("role").equals(ModalSrlRole))) {
					continue;
				}
				if (srlArcs.stream().anyMatch(arc -> arc.getAttributeValue("role").equals(VRole))) {
					continue;
				}

				for (IArc arc : srlArcs) {
					String role = (String) arc.getAttributeValue("role");
					//                    logger.debug("Found token: '{}' with role: '{}'", role, node.getAttributeValue("value"));

					List<INode> argChunk;
					List<INode> flatChunkList = roles.values().stream().flatMap(List::stream).collect(Collectors.toList());
					if (roles.containsKey(role)) {
						if (flatChunkList.contains(node)) {
							continue; // node already in list
						}
						argChunk = roles.get(role);
					} else {
						if (flatChunkList.contains(node)) {
							continue; // node already in list
						}
						argChunk = new ArrayList<>();
					}
					argChunk.add(node);
					roles.put(role, argChunk);
				}
			}
		}

		return new ArrayList<>(roles.values());
	}

}
