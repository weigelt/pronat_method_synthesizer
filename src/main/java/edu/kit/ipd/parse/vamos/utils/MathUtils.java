package edu.kit.ipd.parse.vamos.utils;

import edu.kit.ipd.parse.vamos.programm_representation.FunctionParameterCandidate;

import java.util.*;

public class MathUtils {

    /**
     * Recursive implementation of the cartesian product.
     * Creates every combination of candidates,
     * preserving the different DescriptionParameter chunks.
     * Example: param1 [a,b] and param2 [c,d] -> candidates [a,c] [a,d] [b,c] [b,d]
     * Constraint: 2 elements per chunk
     *
     * @param lists of (here param) candidates in their separated chunks
     * @return all combinations
     */
    public static <T> List<List<T>> cartesianProductListOfLists(List<List<T>> lists) {
        List<List<T>> resultLists = new ArrayList<>();
        if (lists.size() == 0) {
            resultLists.add(new ArrayList<>());
            return resultLists;
        }

        List<T> firstList = lists.get(0);
        List<List<T>> remainingLists = cartesianProductListOfLists(lists.subList(1, lists.size()));
        for (T name : firstList) {
            for (List<T> remainingList : remainingLists) {
                List<T> resultList = new ArrayList<>();
                resultList.add(name);
                resultList.addAll(remainingList);
                resultLists.add(resultList);
            }
        }

        return resultLists;
    }

    /**
     * Find all possible combinations between two lists of different datatypes.
     * Constraint: All elements of LEFT are fixed.
     * There need to be found all combinations of RIGHT to LEFT.
     * See @link edu.kit.ipd.parse.vamos.OntologyMapperTest.testCartesianProductTwoTypes for example output.
     *
     * @param left fixed params
     * @param right params to be mapped
     * @return list of combinations (map) between these two lists
     */
    public static <T, U> List<Map<T, U>> cartesianProductTwoTypes(List<T> left, List<U> right) {
        int diff = left.size() - right.size();
        if (diff > 0) {
            right.addAll(repeat(null, diff));
        }
        List<List<U>> perm = permLen(right, left.size());

        List<Map<T, U>> mapList = new ArrayList<>();
        for (int i = 0; i < perm.size(); i++) {
            Map<T, U> cm = new HashMap<>();
            for (int j = 0; j < left.size(); j++) {
                cm.putIfAbsent(left.get(j), perm.get(i).get(j));
            }
            mapList.add(cm);
        }

        return mapList;
    }


    /**
     * Recursively execute dept first search to get all possible permutations.
     *
     * @param elements elements to permutate
     * @param permutations list of permutation to save results over recursive calls
     * @param result result of the last permutation
     */
    public static <T> void deptFirstSearch(T[] elements, List<List<T>> permutations, List<T> result) {
        if (elements.length == result.size()) {
            List<T> temp = new ArrayList<>(result);
            permutations.add(temp);
        }

        for (T word : elements) {
            if (!result.contains(word)) {
                result.add(word);
                deptFirstSearch(elements, permutations, result);
                result.remove(result.size() - 1);
            }
        }
    }

    private static <T> List<List<T>> permLen(List<T> list, int n) {
        n = (n > list.size()) ? list.size() : n;

        List<List<T>> res = new ArrayList<>();
        for (List<T> p : permutations(list)) {
            List<T> curr = p.subList(0, n);
            if (!res.contains(curr)) {
                res.add(new ArrayList<>(curr));
            }
        }

        return res;
    }

    private static <T> List<List<T>> permutations(List<T> list) {
        List<List<T>> result = new ArrayList<>();
        permHelper(list, list.size(), result);
        return result;
    }

    private static <T> void permHelper(List<T> list, int n, List<List<T>> result) {
        if (n == 1) {
            result.add(new ArrayList<>(list));
            return;
        }

        for (int i = 0; i < n; i++) {
            permHelper(list, n - 1, result);
            if (n % 2 == 1) {
                T tmp = list.get(i);
                list.set(i, list.get(n - 1));
                list.set(n - 1, tmp);
            } else {
                T tmp = list.get(0);
                list.set(0, list.get(n - 1));
                list.set(n - 1, tmp);
            }
        }
    }

    private static <T> List<T> repeat(T item, int diff) {
        if (diff <= 0) {
            return new ArrayList<>();
        }

        List<T> res = new ArrayList<>();
        for (int i = 0; i < diff; i++) {
            res.add(item);
        }

        return res;
    }
}
