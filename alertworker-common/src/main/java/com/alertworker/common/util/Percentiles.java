package com.alertworker.common.util;

import java.util.List;

/**
 * Percentile helpers matching PostgreSQL's {@code PERCENTILE_CONT} semantics: linear interpolation
 * (type 7 / numpy default) over an ascending-sorted sample.
 */
public final class Percentiles {

    private Percentiles() {
    }

    /**
     * @param sortedAsc non-null, ascending-sorted values
     * @param percentile percentile in [0, 100]
     * @return the interpolated percentile, or {@code 0} for an empty sample
     */
    public static double percentile(List<Double> sortedAsc, double percentile) {
        int n = sortedAsc.size();
        if (n == 0) {
            return 0d;
        }
        if (n == 1) {
            return sortedAsc.get(0);
        }
        double rank = percentile / 100.0 * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sortedAsc.get(lo);
        }
        double weight = rank - lo;
        return sortedAsc.get(lo) * (1 - weight) + sortedAsc.get(hi) * weight;
    }
}
