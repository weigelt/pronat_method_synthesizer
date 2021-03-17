package edu.kit.ipd.pronat.vamos;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sebastian Weigelt
 * @author Vanessa Steurer
 */
public enum MulticlassLabels {

	DECL(1), DESC(2), ELSE(3);

	private final int id;
	public static final Map<Integer, MulticlassLabels> LabelLookup = new HashMap<>();

	static {
		for (MulticlassLabels l : MulticlassLabels.values()) {
			LabelLookup.put(l.getId(), l);
		}
	}

	MulticlassLabels(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public static Map<Integer, MulticlassLabels> getLabelLookup() {
		return LabelLookup;
	}
}
