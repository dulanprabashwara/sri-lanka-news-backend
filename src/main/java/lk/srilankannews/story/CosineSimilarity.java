package lk.srilankannews.story;

import java.util.List;

final class CosineSimilarity {
    private CosineSimilarity() {
    }

    static double calculate(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            return 0;
        }
        double dot = 0;
        double leftMagnitude = 0;
        double rightMagnitude = 0;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftMagnitude += a * a;
            rightMagnitude += b * b;
        }
        if (leftMagnitude == 0 || rightMagnitude == 0) {
            return 0;
        }
        double similarity = dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
        return Math.max(-1.0, Math.min(1.0, similarity));
    }
}
