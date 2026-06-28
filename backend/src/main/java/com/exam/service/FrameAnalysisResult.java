package com.exam.service;

import lombok.Data;

/**
 * AI帧分析结果
 */
@Data
public class FrameAnalysisResult {

    /** 是否检测到人脸 */
    private boolean faceDetected;

    /** 检测到的人脸数量 */
    private int faceCount;

    /** 头部偏转角度 (roll, pitch, yaw) */
    private HeadPose headPose;

    /** 是否东张西望 */
    private boolean lookingAway;

    /** 是否检测到违禁物品 */
    private boolean forbiddenDetected;

    /** 违禁物品类型描述 */
    private String forbiddenItems;

    /** 整体置信度 */
    private double confidence;

    /** 处理耗时(毫秒) */
    private long processingTimeMs;

    /** 分析描述 */
    private String description;

    @Data
    public static class HeadPose {
        /** 左右偏转(-180~180, 0为正对) */
        private double yaw;

        /** 上下点头(-90~90, 0为水平) */
        private double pitch;

        /** 头部倾斜(-180~180, 0为直立) */
        private double roll;

        /** 是否异常偏转 */
        private boolean abnormal;

        public HeadPose(double yaw, double pitch, double roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }
    }

    /**
     * 创建"未检测到人脸"结果
     */
    public static FrameAnalysisResult noFace() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setFaceDetected(false);
        result.setFaceCount(0);
        result.setDescription("未检测到人脸");
        return result;
    }

    /**
     * 是否存在需要警告的异常
     */
    public boolean hasAlert() {
        return !faceDetected || faceCount > 1 || lookingAway || forbiddenDetected;
    }
}
