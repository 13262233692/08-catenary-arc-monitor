package com.catenary.arc.util;

import java.util.ArrayList;
import java.util.List;

public class LttbDownsampler {

    public static List<double[]> lttb(double[][] data, int threshold) {
        if (data == null || data.length <= threshold) {
            List<double[]> result = new ArrayList<>();
            if (data != null) {
                for (double[] point : data) {
                    result.add(point.clone());
                }
            }
            return result;
        }

        List<double[]> sampled = new ArrayList<>();
        int dataLength = data.length;

        sampled.add(data[0].clone());

        double bucketSize = (double) (dataLength - 2) / (threshold - 2);

        int a = 0;

        for (int i = 0; i < threshold - 2; i++) {
            int bucketStart = (int) Math.floor((i + 1) * bucketSize) + 1;
            int bucketEnd = (int) Math.floor((i + 2) * bucketSize) + 1;
            bucketEnd = Math.min(bucketEnd, dataLength - 1);

            int avgRangeStart = (int) Math.floor((i + 1) * bucketSize) + 1;
            int avgRangeEnd = (int) Math.floor((i + 2) * bucketSize) + 1;
            avgRangeEnd = Math.min(avgRangeEnd, dataLength);

            double avgX = 0;
            double avgY = 0;
            int avgRangeLength = avgRangeEnd - avgRangeStart;

            for (int j = avgRangeStart; j < avgRangeEnd; j++) {
                avgX += data[j][0];
                avgY += data[j][1];
            }
            avgX /= avgRangeLength;
            avgY /= avgRangeLength;

            double pointAX = data[a][0];
            double pointAY = data[a][1];

            double maxArea = -1;
            int maxAreaIndex = bucketStart;

            for (int j = bucketStart; j < bucketEnd; j++) {
                double area = Math.abs(
                        (pointAX - avgX) * (data[j][1] - pointAY) -
                        (pointAX - data[j][0]) * (avgY - pointAY)
                ) * 0.5;

                if (area > maxArea) {
                    maxArea = area;
                    maxAreaIndex = j;
                }
            }

            sampled.add(data[maxAreaIndex].clone());
            a = maxAreaIndex;
        }

        sampled.add(data[dataLength - 1].clone());

        return sampled;
    }

    private LttbDownsampler() {
    }
}
