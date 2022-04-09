/*
 * Copyright (c) 2022 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.utils.sort;

import java.util.Arrays;
import java.util.List;

/**
 * The common interface of most sorting algorithms
 *
 * @author Podshivalov Nikita (https://github.com/nikitap492)
 */
public interface SortAlgorithm {

    /**
     * Main method arrays sorting algorithms
     *
     * @param unsorted - an array should be sorted
     * @return a sorted array
     */
    <T extends Comparable<T>> T[] sort(T[] unsorted);

    /**
     * Auxiliary method for algorithms what wanted to work with lists from JCF
     *
     * @param unsorted - a list should be sorted
     * @return a sorted list
     */
    @SuppressWarnings("unchecked")
    default <T extends Comparable<T>> List<T> sort(List<T> unsorted) {
        return Arrays.asList(sort(unsorted.toArray((T[]) new Comparable[unsorted.size()])));
    }

    /**
     * Helper method for swapping places in array
     *
     * @param array The array which elements we want to swap
     * @param idx   index of the first element
     * @param idy   index of the second element
     */
    default <T> boolean swap(T[] array, int idx, int idy) {
        T swap = array[idx];
        array[idx] = array[idy];
        array[idy] = swap;
        return true;
    }

    /**
     * This method checks if first element is less than the other element
     *
     * @param v first element
     * @param w second element
     * @return true if the first element is less than the second element
     */
    default <T extends Comparable<T>> boolean less(T v, T w) {
        return v.compareTo(w) < 0;
    }

    /**
     * This method checks if first element is greater than the other element
     *
     * @param v first element
     * @param w second element
     * @return true if the first element is greater than the second element
     */
    default <T extends Comparable<T>> boolean greater(T v, T w) {
        return v.compareTo(w) > 0;
    }

    /**
     * This method checks if first element is greater than or equal the other
     * element
     *
     * @param v first element
     * @param w second element
     * @return true if the first element is greater than or equal the second
     * element
     */
    default <T extends Comparable<T>> boolean greaterOrEqual(T v, T w) {
        return v.compareTo(w) >= 0;
    }

    /**
     * Prints a list
     *
     * @param toPrint - a list which should be printed
     */
    default void print(List<?> toPrint) {
        toPrint.stream().map(Object::toString).map(str -> str + " ").forEach(System.out::print);

        System.out.println();
    }

    /**
     * Prints an array
     *
     * @param toPrint - an array which should be printed
     */
    default void print(Object[] toPrint) {
        System.out.println(Arrays.toString(toPrint));
    }

    /**
     * Swaps all position from {
     *
     * @param left}  to @{
     * @param right} for {
     * @param array}
     * @param array  is an array
     * @param left   is a left flip border of the array
     * @param right  is a right flip border of the array
     */
    default <T extends Comparable<T>> void flip(T[] array, int left, int right) {
        while (left <= right) {
            swap(array, left++, right--);
        }
    }

}