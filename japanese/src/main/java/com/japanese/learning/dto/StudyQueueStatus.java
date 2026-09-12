package com.japanese.learning.dto;

public record StudyQueueStatus(boolean queued, boolean canAdd, String label) {
    public static StudyQueueStatus ready() {
        return new StudyQueueStatus(false, true, "학습 목록에 추가");
    }

    public static StudyQueueStatus alreadyQueued() {
        return new StudyQueueStatus(true, true, "학습 목록에 있음");
    }

    public static StudyQueueStatus alreadyLearning() {
        return new StudyQueueStatus(false, false, "이미 학습 중");
    }
}
