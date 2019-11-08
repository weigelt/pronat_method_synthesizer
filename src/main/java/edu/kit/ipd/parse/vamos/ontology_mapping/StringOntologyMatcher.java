package edu.kit.ipd.parse.vamos.ontology_mapping;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Collections2;
import edu.kit.ipd.parse.ontology_connection.Domain;
import edu.kit.ipd.parse.ontology_connection.IDomain;
import edu.kit.ipd.parse.ontology_connection.IIndividual;
import edu.kit.ipd.parse.ontology_connection.ITypedClassContainer;
import edu.kit.ipd.parse.ontology_connection.datatype.IDataType;
import edu.kit.ipd.parse.ontology_connection.method.IMethod;
import edu.kit.ipd.parse.ontology_connection.object.IObject;
import edu.kit.ipd.parse.ontology_connection.search_strategy.Fuzzy;
import edu.kit.ipd.parse.ontology_connection.search_strategy.ISearchStrategy;
import edu.kit.ipd.parse.ontology_connection.search_strategy.JaroWinkler;
import edu.kit.ipd.parse.ontology_connection.state.IState;
import edu.kit.ipd.parse.ontology_connection.value.IValue;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StringOntologyMatcher {

	private static final Logger logger = LoggerFactory.getLogger(StringOntologyMatcher.class);
	private static IDomain domain = Domain.getInstance();

	private static MyJaroWinkler jaroWinkler = new MyJaroWinkler(0.40f);

	private boolean usePermutations = false;

	public StringOntologyMatcher(boolean usePermutations) {
		this.usePermutations = usePermutations;
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
				for (String substring : individualName.split("\\.")) { // split individual words; e.g. Dishwasher.Door -> "Dishwasher", "Door"
					individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName); // e.g. CoffeeMachine -> coffee_machine
					individualName = individualName.replace("_", " ") // remove underscores e.g. coffee_machine -> coffee machine
							.replaceAll("[0-9]", ""); // remove numbers  e.g. move1 -> move

					score += jwd.apply(searchString, substring);
				}
				return score / 2;
			} else {
				individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName);
				individualName = individualName.replace("_", " ") // remove underscores e.g. coffee_machine -> coffee machine
						.replaceAll("[0-9]", ""); // remove numbers  e.g. move1 -> move

				return jwd.apply(searchString, individualName);
			}
		}
	}

	//private static MyFuzzy fuzzySearch = new MyFuzzy(0.15f);
	private static Fuzzy fuzzySearch = new Fuzzy(0.15f);

	private static class MyFuzzy extends Fuzzy {
		private final FuzzyScore fs;

		MyFuzzy(float threshold) {
			super(threshold);
			fs = new FuzzyScore(Locale.ENGLISH);
		}

		@Override
		public double score(String searchString, IIndividual individual) {
			String individualName = individual.getName();
			individualName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, individualName); // Dishwasher.Door -> dishwasher._door
			individualName = individualName.replace("_", " ") // remove underscores e.g. dishwasher._door -> dishwasher. door
					.replace(".", "") // remove dots e.g. dishwasher. door -> dishwasher door
					.replaceAll("[0-9]", ""); // remove numbers e.g. move1 -> move

			int length = searchString.length() > individualName.length() ? searchString.length() : individualName.length();
			int maxScore = 3 * length - 2;

			return ((double) fs.fuzzyScore(individualName, searchString)) / ((double) maxScore);
		}
	}

	/**
	 * Find matches between the given method names and the elements of the connected
	 * ontology. Use an ensemble of two string distance metrics: Jaro Winkler and
	 * Fuzzy Search.
	 *
	 * @param combinedMethodName
	 *            list of extracted method names to search for
	 * @param combinedNamesUnlematized
	 * @return list of possible method matches, each ImmutablePaired with a double
	 *         similarity score
	 */
	public List<ImmutablePair<Double, IMethod>> getMethodEnsembleMatches(List<String> combinedMethodName,
			List<String> combinedNamesUnlematized) {
		logger.debug("Get possible method string matches from {} combined names with ensemble metric.", combinedMethodName.size());

		List<ImmutablePair<Double, IMethod>> jwMatches = new ArrayList<>();
		List<ImmutablePair<Double, IMethod>> fsMatches = new ArrayList<>();

		if (usePermutations && combinedMethodName.size() < 6) {
			calculatePermutated(combinedMethodName, jwMatches, fsMatches);
		} else {
			calculate(combinedMethodName, jwMatches, fsMatches);
		}
		if (combinedNamesUnlematized != null) {
			if (usePermutations && combinedNamesUnlematized.size() < 6) {
				calculatePermutated(combinedNamesUnlematized, jwMatches, fsMatches);
			} else {
				calculate(combinedNamesUnlematized, jwMatches, fsMatches);
			}
		}

		// can contain multiple matches to the same IMethod -> get best score of each unique ontology item each
		Map<IMethod, Double> jwMatchesMap = getBestScorePerMethodMatch(jwMatches);
		Map<IMethod, Double> fsMatchesMap = getBestScorePerMethodMatch(fsMatches);

		// get overlap of those matches. if no overlap, return empty list
		return getOverlappingMethodMatches(jwMatchesMap, fsMatchesMap);
	}

	private void calculatePermutated(List<String> in, List<ImmutablePair<Double, IMethod>> jwMatches,
			List<ImmutablePair<Double, IMethod>> fsMatches) {
		for (String s : in) {
			Set<String> singleSet = Stream.of(s.split(" ")).map(e -> new String(e)).collect(Collectors.toSet());
			for (List<String> permutations : Collections2.orderedPermutations(singleSet)) {
				jwMatches.addAll(domain.getMethods().getMemberBySearchStringAsMap(String.join("", permutations), jaroWinkler));
				fsMatches.addAll(domain.getMethods().getMemberBySearchStringAsMap(String.join("", permutations), fuzzySearch));
			}
		}
	}

	private void calculate(List<String> in, List<ImmutablePair<Double, IMethod>> jwMatches,
			List<ImmutablePair<Double, IMethod>> fsMatches) {
		for (String name : in) {
			jwMatches.addAll(domain.getMethods().getMemberBySearchStringAsMap(name, jaroWinkler));
			fsMatches.addAll(domain.getMethods().getMemberBySearchStringAsMap(name, fuzzySearch));
		}
	}

	/**
	 * Find matches between the given parameter names and the elements of the
	 * connected ontology. Use an ensemble of two string distance metrics: Jaro
	 * Winkler and Fuzzy Search.
	 * 
	 * @param permutedParameterName
	 *            list of extracted parameter names to search for
	 * @return list of possible parameter matches, each ImmutablePaired with a
	 *         double similarity score
	 */
	public List<ImmutablePair<Double, IIndividual>> getParameterEnsembleMatches(List<String> permutedParameterName) {
		logger.debug("Get possible parameter string matches for {} permuted names with ensemble metric.", permutedParameterName.size());

		List<ImmutablePair<Double, IIndividual>> jwMatches = new ArrayList<>();
		List<ImmutablePair<Double, IIndividual>> fsMatches = new ArrayList<>();
		for (String name : permutedParameterName) {
			jwMatches.addAll(getParameterMatchesByStrategy(name, jaroWinkler));
			fsMatches.addAll(getParameterMatchesByStrategy(name, fuzzySearch));
		}

		// get best score of each unique ontology item each
		Map<IIndividual, Double> jwMatchesMap = getBestScoreOfParamMatches(jwMatches);
		Map<IIndividual, Double> fsMatchesMap = getBestScoreOfParamMatches(fsMatches);

		// get overlap of those matches. if no overlap, return empty list
		return getOverlappingParamMatches(jwMatchesMap, fsMatchesMap);
	}

	private List<ImmutablePair<Double, IIndividual>> getParameterMatchesByStrategy(String name, ISearchStrategy strategy) {
		List<ImmutablePair<Double, IIndividual>> matches = new ArrayList<>();

		List<ImmutablePair<Double, IObject>> memberObjects = domain.getObjects().getMemberBySearchStringAsMap(name, strategy);
		for (ImmutablePair<Double, IObject> member : memberObjects) {
			if (member.getRight().getName().equals("Person")) { // special case for placeholder Person
				matches.add(new ImmutablePair<>(member.getKey() / 2.0, (IIndividual) member.getValue()));
			} else {
				matches.add(new ImmutablePair<>(member.getKey(), (IIndividual) member.getValue()));
			}
		}

		Set<String> types = domain.getTypedObjects().getTypes();
		List<ImmutablePair<Double, IValue>> memberValues = domain.getValues().getMemberBySearchStringAsMap(name, strategy);
		for (ImmutablePair<Double, IValue> member : memberValues) {
			if (types.contains(member.getRight().getName())) {
				continue; // skip typedobject as IValue e.g. (Drinkable)
			}

			matches.add(new ImmutablePair<>(member.getKey(), (IIndividual) member.getValue()));
		}

		List<ImmutablePair<Double, IState>> memberStates = domain.getStates().getMemberBySearchStringAsMap(name, strategy);
		for (ImmutablePair<Double, IState> member : memberStates) {
			matches.add(new ImmutablePair<>(member.getKey(), (IIndividual) member.getValue()));
		}

		// logger.debug("Found {} parameter matches for String '{}' with strategy {}:", matches.size(), name, strategy);
		// matches.forEach(m -> logger.debug(m.getValue().getName() + "(" + m.getKey() + "); "));
		return matches;
	}

	/**
	 * Get a map of ontology item matches with their similarity score. Only save the
	 * best score for each unique item.
	 *
	 * @param matches
	 *            the given matches
	 * @return the map of item with its best similarity score
	 */
	private Map<IMethod, Double> getBestScorePerMethodMatch(List<ImmutablePair<Double, IMethod>> matches) {
		// only add item once
		Map<IMethod, Double> matchesMap = new HashMap<>();
		for (ImmutablePair<Double, IMethod> match : matches) {
			IMethod method = match.getValue();
			double similarityScore = match.getKey();

			// only add item, if the score is higher
			if (matchesMap.containsKey(method)) {
				if (similarityScore > matchesMap.get(method)) {
					matchesMap.put(method, similarityScore);
				}

			} else {
				matchesMap.put(method, similarityScore);
			}
		}

		return matchesMap;
	}

	/**
	 * Get a map of ontology item matches with their similarity score. Only save the
	 * best score for each unique item.
	 *
	 * @param matches
	 *            the given matches
	 * @return the map of item with its best similarity score
	 */
	private Map<IIndividual, Double> getBestScoreOfParamMatches(List<ImmutablePair<Double, IIndividual>> matches) {
		// only add item once
		Map<IIndividual, Double> matchesMap = new HashMap<>();
		for (ImmutablePair<Double, IIndividual> match : matches) {
			IIndividual param = match.getValue();
			double similarityScore = match.getKey();

			// only add item, if the score is higher
			if (matchesMap.containsKey(param)) {
				if (similarityScore > matchesMap.get(param)) {
					matchesMap.put(param, similarityScore);
				}
			} else {
				matchesMap.put(param, similarityScore);
			}
		}

		return matchesMap;
	}

	/**
	 * Find overlap of method matches between the list of found matches of the two
	 * distance metrics.
	 * 
	 * @param jw
	 *            jaro winkler method matches
	 * @param fs
	 *            fuzzy search method matches
	 * @return List of overlapping method matches, each ImmutablePaired with the
	 *         double similarity score
	 */
	private List<ImmutablePair<Double, IMethod>> getOverlappingMethodMatches(Map<IMethod, Double> jw, Map<IMethod, Double> fs) {
		List<ImmutablePair<Double, IMethod>> overlaps = new ArrayList<>();
		for (IMethod overlap : jw.keySet()) {

			// get IMethod overlap in the two search strategies
			if (fs.containsKey(overlap)) {
				// return averaged score with overlapping strategies
				double avgScore = (jw.get(overlap) + fs.get(overlap)) / 2.0;
				overlaps.add(new ImmutablePair<>(avgScore, overlap));
			}
		}

		logger.debug("Keep only overlapping method matches. Reduce from {} to {} matches.", (jw.size() + fs.size()), overlaps.size());
		overlaps.forEach(m -> logger.debug(m.getValue().getName() + "(" + m.getKey() + "); "));
		return overlaps;
	}

	/**
	 * Find overlap of parameter matches between the list of found matches of the
	 * two distance metrics.
	 * 
	 * @param jw
	 *            jaro winkler parameter matches
	 * @param fs
	 *            fuzzy search parameter matches
	 * @return List of overlapping parameter matches, each ImmutablePaired with the
	 *         double similarity score
	 */
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
					logger.debug("drop overlap match {} with avg score {} <= 0.4)", overlap.getName(), avgScore);
				}
			}
		}

		logger.debug("Keep only overlapping parameter matches. Reduce from {} to {} matches.", (jw.size() + fs.size()), overlaps.size());
		overlaps.forEach(m -> logger.debug(m.getValue().getName() + "(" + m.getKey() + "); "));
		return overlaps;
	}

	public IObject checkForTypedObjects(String name, IDataType type) {
		ITypedClassContainer<IObject> typedObjects = domain.getTypedObjects();

		// ontology parameter is no typed object
		if (!typedObjects.getTypes().contains(type.getName())) {
			return null;
		}

		// ontology parameter is typed object, find matching individual for extracted param
		return domain.getTypedObjects().getMemberByNameAndType(name, type.getName());
	}

	/**
	 * Get suffix of ontology function name and try to (sub)string match them with
	 * the given parameter name. e.g. lookAt -> At, lowerHead -> Head, liftRightHand
	 * -> RightHand
	 *
	 * @param ontologyFunctionName
	 *            function name
	 * @param parameterName
	 *            parameter
	 * @return similarity score with jaro winkler
	 */
	public boolean checkForSubstring(String ontologyFunctionName, String parameterName) {
		JaroWinklerDistance jwd = new JaroWinklerDistance();

		// CamelCase -> lower_case
		ontologyFunctionName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, ontologyFunctionName);
		int indexWordsAfterFunctionVerb = ontologyFunctionName.indexOf("_");
		if (indexWordsAfterFunctionVerb > -1) {

			// get suffix of function name by cutting off the verb
			ontologyFunctionName = ontologyFunctionName.substring(indexWordsAfterFunctionVerb + 1);
			for (String functionNameSuffix : ontologyFunctionName.split("_")) {

				if (parameterName.toLowerCase().contains(functionNameSuffix)) {
					logger.debug("Found substring ontology method '{}' in instruction parameter / parameter candidate '{}'",
							functionNameSuffix, parameterName);
					return true;
				}
			}
		}

		return false;
	}

}
