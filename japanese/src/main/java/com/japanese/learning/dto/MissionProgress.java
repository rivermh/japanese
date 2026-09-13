package com.japanese.learning.dto;

public record MissionProgress(String type, String label, int target, int completed) {
    public int remaining() { return Math.max(target - completed, 0); }
    public boolean done() { return target == 0 || completed >= target; }
}
