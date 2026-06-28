package com.exam.service;

import lombok.Data;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.List;

/**
 * 人脸检测结果
 */
@Data
public class FaceDetectionResult {

    /** 是否检测到人脸 */
    private boolean detected;

    /** 检测到的人脸数量 */
    private int faceCount;

    /** 人脸区域列表 */
    private List<FaceRect> faceRects;

    /** 最主要的人脸(最大/最居中的) */
    private FaceRect primaryFace;

    /** 处理耗时(毫秒) */
    private long processingTimeMs;

    @Data
    public static class FaceRect {
        private int x;
        private int y;
        private int width;
        private int height;
        private double confidence;

        public FaceRect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = 1.0;
        }

        public FaceRect(Rect rect) {
            this.x = rect.x();
            this.y = rect.y();
            this.width = rect.width();
            this.height = rect.height();
            this.confidence = 1.0;
        }

        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }
    }
}
