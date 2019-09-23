package edu.kit.ipd.parse.vamos;

import com.google.common.base.CaseFormat;
import edu.kit.ipd.parse.luna.tools.ConfigManager;
import edu.kit.ipd.parse.ontology_connection.Domain;
import edu.kit.ipd.parse.ontology_connection.IDomain;
import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.ontology_connection.method.IMethod;
import edu.kit.ipd.parse.ontology_connection.object.IObject;
import edu.kit.ipd.parse.ontology_connection.parameter.IParameter;
import edu.kit.ipd.parse.ontology_connection.search_strategy.*;
import edu.kit.ipd.parse.ontology_connection.state.IState;
import edu.kit.ipd.parse.vamos.ontology_mapping.StringOntologyMatcher;
import edu.kit.ipd.parse.luna.graph.Pair;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class StringOntologyMatcherStategyTest {

	private static IDomain domain;
	private static Properties props;
	private static ISearchStrategy jwStrategy;
	private static ISearchStrategy fuzzyStrategy;
	private static ISearchStrategy levenstein;
	private static ISearchStrategy fuzzyWuzzyStrategy;
	private static MyJaroWinkler myJaroWinkler;
	private static MyFuzzy myFuzzySearch;

	@BeforeClass
	public static void SetUp() {
		props = ConfigManager.getConfiguration(Domain.class);
		props.setProperty("ONTOLOGY_PATH", "/vamos_ontology.owl");
		domain = Domain.getInstance();
		assertNotNull(domain);

		jwStrategy = new JaroWinkler();
		myJaroWinkler = new MyJaroWinkler(0.40f);

		fuzzyStrategy = new Fuzzy();
		myFuzzySearch = new MyFuzzy(0.15f);

		levenstein = new Levenshtein();
		fuzzyWuzzyStrategy = new FuzzyWuzzy();
	}

	private static class MyJaroWinkler extends JaroWinkler {
		JaroWinklerDistance jwd = new JaroWinklerDistance();

		MyJaroWinkler(float threshold) {
			super(threshold);
		}

		@Override
		public double score(String searchString, IIndividual individual) {
			String individualName = individual.getName();

			double score = 0;
			if (individualName.split("\\.").length > 1) {
				for (String substring : individualName.split("\\.")) {
					individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName);
					individualName = individualName.replace("_", " ") // remove underscores
							.replaceAll("[0-9]", ""); // remove numbers

					score += jwd.apply(searchString, substring);
				}
				return score / 2;
			} else {
				individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName);
				individualName = individualName.replace("_", " ") // remove underscores
						.replaceAll("[0-9]", ""); // remove numbers

				return jwd.apply(searchString, individualName);
			}
		}
	}

	private static class MyFuzzy extends Fuzzy {
		private final FuzzyScore fs;

		MyFuzzy(float threshold) {
			super(threshold);
			fs = new FuzzyScore(Locale.ENGLISH);
		}

		@Override
		public double score(String searchString, IIndividual individual) {
			String individualName = individual.getName();
			individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName);
			individualName = individualName.replace("_", " ") // remove underscores
					.replace(".", " ") // remove dots
					.replaceAll("[0-9]", "") // remove numbers
					.replace("  ", " "); // replace doubled spaces

			int length = searchString.length() > individualName.length() ? searchString.length() : individualName.length();
			int maxScore = 3 * length - 2;

			return ((double) fs.fuzzyScore(individualName, searchString)) / ((double) maxScore);
		}
	}

	@Test
	public void compareStrategies() {
		JaroWinklerDistance jw = new JaroWinklerDistance();
		FuzzyScore fuzzy = new FuzzyScore(Locale.ENGLISH);
		LevenshteinDistance le = new LevenshteinDistance();

		List<Pair<String, String>> examples = getExamples();
		for (Pair<String, String> toMatch : examples) {
			String s1 = toMatch.getLeft();
			String s2 = toMatch.getRight();
			int length = s1.length() > s2.length() ? s1.length() : s2.length();
			int fuzzyMaxScore = (3 * length) - 2;

			System.out.println("------" + s1 + " - " + s2);

			System.out.println("fuzzywuzzy: " + (double) FuzzySearch.ratio(s1, s2) / 100);
			System.out.println("jaro winkler: " + jw.apply(s1, s2));
			System.out.println("fuzzy: " + ((double) fuzzy.fuzzyScore(s1, s2)) / (double) fuzzyMaxScore);
			System.out.println("levenstein: " + (1d - ((double) le.apply(s1, s2)) / (double) length));

			System.out.println("------");
		}
	}

	private List<Pair<String, String>> getExamples() {
		List<Pair<String, String>> examples = new ArrayList<>();
		examples.add(new Pair<>("red button", "CoffeeMachine.RedButton"));
		examples.add(new Pair<>("red button", "coffeeMachine redButton"));
		examples.add(new Pair<>("red button", "coffee machine red button"));

		examples.add(new Pair<>("red button", "Microwave.RedButton"));
		examples.add(new Pair<>("red button", "microwave redButton"));
		examples.add(new Pair<>("red button", "microwave red button"));

		//        examples.add(new Pair<>("the dishes", "Dishes"));
		//        examples.add(new Pair<>("dishes", "Dishes"));
		//        examples.add(new Pair<>("fridgeDoor", "Doorfridge"));
		//        examples.add(new Pair<>("fridgeDoor", "doorfridge"));
		//        examples.add(new Pair<>("fridge door", "door fridge"));
		//        examples.add(new Pair<>("door of the dishwasher", "disherwasherdoor"));
		//        examples.add(new Pair<>("door of the dishwasher", "dishwasher.door"));
		//        examples.add(new Pair<>("dishwasherdoor", "dishwasher"));
		//        examples.add(new Pair<>("dishwasherdoor", "doordishwasher"));

		return examples;
	}

	public static class FuzzyWuzzy extends AbstractSearchStrategy {
		FuzzyWuzzy() {
			super();
		}

		@Override
		public double score(String searchString, IIndividual individual) {
			return (double) FuzzySearch.ratio(searchString, individual.getName()) / 100;
		}
	}

	@Test
	public void compareStrategiesWithMethods() {
		String searchstring = "press";

		System.out.println("\n JW");
		List<ImmutablePair<Double, IMethod>> jw_items = domain.getMethods().getMemberBySearchStringAsMap(searchstring, jwStrategy);
		for (ImmutablePair<Double, IMethod> match : jw_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n FUZZY");
		List<ImmutablePair<Double, IMethod>> fuzzy_items = domain.getMethods().getMemberBySearchStringAsMap(searchstring, fuzzyStrategy);
		for (ImmutablePair<Double, IMethod> match : fuzzy_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n LEV");
		List<ImmutablePair<Double, IMethod>> lev_items = domain.getMethods().getMemberBySearchStringAsMap(searchstring, levenstein);
		for (ImmutablePair<Double, IMethod> match : lev_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n FW");
		List<ImmutablePair<Double, IMethod>> fw_items = domain.getMethods().getMemberBySearchStringAsMap(searchstring, fuzzyWuzzyStrategy);
		for (ImmutablePair<Double, IMethod> match : fw_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}
	}

	@Test
	public void compareStrategiesWithObjects() {
		String searchstring = "desk";

		System.out.println("\n JW");
		List<ImmutablePair<Double, IObject>> jw_items = domain.getObjects().getMemberBySearchStringAsMap(searchstring, jwStrategy);
		for (ImmutablePair<Double, IObject> match : jw_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n FUZZY");
		List<ImmutablePair<Double, IObject>> fuzzy_items = domain.getObjects().getMemberBySearchStringAsMap(searchstring, fuzzyStrategy);
		for (ImmutablePair<Double, IObject> match : fuzzy_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n LEV");
		List<ImmutablePair<Double, IObject>> lev_items = domain.getObjects().getMemberBySearchStringAsMap(searchstring, levenstein);
		for (ImmutablePair<Double, IObject> match : lev_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n FW");
		List<ImmutablePair<Double, IObject>> fw_items = domain.getObjects().getMemberBySearchStringAsMap(searchstring, fuzzyWuzzyStrategy);
		for (ImmutablePair<Double, IObject> match : fw_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}
	}

	@Test
	public void getOverlappingObjects() {
		String searchString = "machine";
		// Find function parameter candidates for extracted function parameter 'machine'.

		System.out.println("\n JW");
		List<ImmutablePair<Double, IObject>> jw_items = domain.getObjects().getMemberBySearchStringAsMap(searchString, myJaroWinkler);
		for (ImmutablePair<Double, IObject> match : jw_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		System.out.println("\n FUZZY");
		List<ImmutablePair<Double, IObject>> fuzzy_items = domain.getObjects().getMemberBySearchStringAsMap(searchString, myFuzzySearch);
		for (ImmutablePair<Double, IObject> match : fuzzy_items) {
			System.out.println(match.getValue().getName() + " : " + match.getKey());
		}

		// get best score of each unique ontology item each
		Map<IIndividual, Double> jwMatchesMap = getBestScoreOfParamMatches(jw_items);
		Map<IIndividual, Double> fsMatchesMap = getBestScoreOfParamMatches(fuzzy_items);

		// get overlap of those matches. if no overlap, return empty list
		getOverlappingParamMatches(jwMatchesMap, fsMatchesMap);
	}

	@Test
	public void findIObjects() {
		List<ImmutablePair<Double, IMethod>> mmatch = domain.getMethods().getMemberBySearchStringAsMap("close", jwStrategy);
		IMethod m = mmatch.get(0).getValue();
		System.out.println(m.getName());
		System.out.println(m.getParameters().size());

		IParameter ontologyParamToMap = m.getFirstParameter();
		System.out.println(ontologyParamToMap.getName());
		System.out.println(ontologyParamToMap.getDataType());
		System.out.println(ontologyParamToMap.getDataType().getName());

		System.out.println("------------------");

		List<ImmutablePair<Double, IObject>> omatch = domain.getObjects().getMemberBySearchStringAsMap("microwave", jwStrategy);
		for (ImmutablePair<Double, IObject> obj : omatch) {
			System.out.println(obj.getValue().getName() + " : " + obj.getKey());

			IObject s = obj.getValue();
			System.out.println("obj name: " + s.getName());
			System.out.println("obj class: " + s.getClass());
			System.out.println("obj types: " + Arrays.toString(s.getTypes().toArray()));

			System.out.println(domain.getTypedObjects().getTypes());

			IObject typedObject = domain.getTypedObjects().getMemberByNameAndType(s.getName(), ontologyParamToMap.getDataType().getName());
			System.out.println("typedObj = " + typedObject);

			// one of the sub-objects of this object have the same data type
			if (s.getSubObjects().stream().anyMatch(subobj -> (domain.getTypedObjects().getMemberByNameAndType(subobj.getName(),
					ontologyParamToMap.getDataType().getName()) != null))) {
				List<IObject> subObject = s.getSubObjects().stream()
						.filter(subobj -> subobj.getTypes().contains(ontologyParamToMap.getDataType().getName()))
						.collect(Collectors.toList());
				System.out.println("typedObj = " + subObject);
				System.out.println("param.DATATYPE EQUALS obj.TYPEDOBJ");

				Set<IObject> subObjects = s.getSubObjects();
				System.out.println(subObjects.size());
				if (subObjects.size() > 0) {
					IObject o2 = subObjects.iterator().next();
					System.out.println("subobj name: " + o2.getName());
					System.out.println("subobj class: " + o2.getClass());
					System.out.println("subobj types: " + Arrays.toString(o2.getTypes().toArray()));
				}
				System.out.println("------------------");
			}
		}
	}

	@Test
	public void findIState() {
		List<ImmutablePair<Double, IMethod>> mmatch = domain.getMethods().getMemberBySearchStringAsMap("turn", jwStrategy);
		System.out.println(mmatch.get(0).getValue().getName());
		if (mmatch.get(0).getValue().hasParameter()) {
			System.out.println(mmatch.get(0).getValue().getFirstParameter().getName());
			System.out.println(mmatch.get(0).getValue().getFirstParameter().getDataType().getName());
			System.out.println(mmatch.get(0).getValue().getFirstParameter().getDataType().getFirstValue().getName());
		}

		System.out.println("---------");

		List<ImmutablePair<Double, IState>> smatch = domain.getStates().getMemberBySearchStringAsMap("on", jwStrategy);
		for (ImmutablePair<Double, IState> state : smatch) {
			System.out.println(state.getValue().getName() + " : " + state.getKey());

			IState s = state.getValue();
			System.out.println(s.getName());
			System.out.println(s.getClass());

			System.out.println("------------------");
		}
	}

	private Map<IIndividual, Double> getBestScoreOfParamMatches(List<ImmutablePair<Double, IObject>> matches) {
		// only add item once
		Map<IIndividual, Double> matchesMap = new HashMap<>();
		for (ImmutablePair<Double, IObject> match : matches) {
			IIndividual param = match.getValue();
			double similarityScore = match.getKey();

			// only add item, if the score is higher
			if (matchesMap.containsKey(param)) {
				if (similarityScore > matchesMap.get(param)) {
					matchesMap.put(param, similarityScore);
				} else {
					System.out.println("Skip param " + param.getName() + " score: " + matchesMap.get(param));
				}
			} else {
				matchesMap.put(param, similarityScore);
			}
		}

		return matchesMap;
	}

	private List<ImmutablePair<Double, IIndividual>> getOverlappingParamMatches(Map<IIndividual, Double> jw, Map<IIndividual, Double> fs) {
		List<ImmutablePair<Double, IIndividual>> overlaps = new ArrayList<>();
		for (IIndividual overlap : jw.keySet()) {

			// get IIndividual overlap in the two search strategies
			if (fs.containsKey(overlap)) {
				// return averaged score with overlapping strategies
				double avgScore = (jw.get(overlap) + fs.get(overlap)) / 2.0;
				if (avgScore > 0.4) {
					overlaps.add(new ImmutablePair<>(avgScore, overlap));
				}
				if (avgScore <= 0.4) {
					System.out.println("DROP OVERLAP MATCH " + overlap.getName() + " with avgscore " + avgScore);
				}
			}
		}

		System.out.println("\nFound overlaps:");
		overlaps.forEach(m -> System.out.println(m.getValue().getName() + "(" + m.getKey() + "); "));
		return overlaps;
	}
}
