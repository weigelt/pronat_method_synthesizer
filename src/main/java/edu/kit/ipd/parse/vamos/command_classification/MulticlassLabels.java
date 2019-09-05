package edu.kit.ipd.parse.vamos.command_classification;

import java.util.HashMap;
import java.util.Map;

public enum MulticlassLabels {

    DECL(1),
    DESC(2),
    ELSE(3);

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
