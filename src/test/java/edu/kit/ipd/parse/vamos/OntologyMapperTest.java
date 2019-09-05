package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.ontology_connection.Domain;
import edu.kit.ipd.parse.ontology_connection.IDomain;
import edu.kit.ipd.parse.ontology_connection.object.IObject;
import edu.kit.ipd.parse.ontology_connection.value.IValue;
import edu.kit.ipd.parse.vamos.utils.MathUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class OntologyMapperTest {
    private static IDomain domain;

    @BeforeClass
    public static void SetUp() {
        domain = Domain.getInstance();
        assertNotNull(domain);
    }

    @Test
    public void testGetObjectByNameAndTypeUntrue() {
        String input = "Fridge";
        assertNull(domain.getTypedObjects().getMemberByNameAndType(input, "Closeable"));
    }

    @Test
    public void testGetObjectBySubstringAndType() {
        String inputSubstring = "Door";
        String type = "Closeable";
        Set<IObject> expectedSet = Set.of(domain.getObjects().getMemberByName("Microwave.Door"),
                domain.getObjects().getMemberByName("Dishwasher.Door"), domain.getObjects().getMemberByName("Cupboard.Door"),
                domain.getObjects().getMemberByName("Fridge.Door"));
        Assert.assertEquals(expectedSet, domain.getTypedObjects().getMemberByNameSubstringAndType(inputSubstring, type, true));
    }

    @Test
    public void testGetObjectByNameAndTypeTrue() {
        String input = "Fridge.Door";
        assertEquals(domain.getObjects().getMemberByName("Fridge.Door"),
                domain.getTypedObjects().getMemberByNameAndType(input, "Closeable"));
    }

    @Test
    public void testGetSubObjectByNameAndTypeTrue() {
        String input = "CocoaBox";
        IObject memberByName = domain.getObjects().getMemberByName(input);
        memberByName.hasSubObjects();
    }

    @Test
    public void testTypedObj() {
        String input = "Openable";
        IValue memberByName = domain.getValues().getMemberByName(input);
        System.out.println(memberByName.getName());
        System.out.println(memberByName.getFullName());
    }

    @Test
    public void testCartesianProductTwoTypes() {
        List<String> list1 = new ArrayList<>();
        list1.add("a");
        list1.add("b");
        list1.add("c");
        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(1);

        List<Map<String, Integer>> maps = MathUtils.cartesianProductTwoTypes(list1, list2);
        for (Map<String, Integer> map : maps) {
            StringJoiner joiner = new StringJoiner(" ");
            joiner.add("[");
            for (String s : map.keySet()) {
                joiner.add(s + ":" + map.get(s) + "; ");
            }
            joiner.add("]");
            System.out.println(joiner.toString());
        }

        /* returns
        [ a:1;  b:1;  c:null;  ]
        [ a:null;  b:x;  c:1;  ]
        [ a:1;  b:null;  c:1;  ]
        */
    }
}
