package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.mapper.VideoEvidenceMapper;
import com.exam.mapper.ViolationRecordMapper;
import com.exam.pojo.VideoEvidence;
import com.exam.pojo.ViolationRecord;
import com.exam.websocket.ExamMonitorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频证据留存服务
 * 
 * 功能:
 * 1. 违规时录制短视频片段（从帧缓存中取最近N帧）
 * 2. 生成缩略图
 * 3. 管理证据文件生命周期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoEvidenceService {

    private final VideoEvidenceMapper videoEvidenceMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final FaceCompareService faceCompareService;
    private final ExamMonitorWebSocketHandler wsHandler;

    @Value("${app.video.duration:10}")
    private int recordDuration;

    @Value("${app.video.enabled:true}")
    private boolean videoEnabled;

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    /** 帧缓存: examId_studentId -> 帧列表(时间戳 -> JPEG数据) */
    private final Map<String, LinkedHashMap<Long, byte[]>> frameBuffer = new ConcurrentHashMap<>();

    /** 最大缓存帧数 */
    private static final int MAX_BUFFER_FRAMES = 300; // 约30秒(10fps)

    /**
     * 添加帧到缓存
     */
    public void addFrame(long examId, long studentId, byte[] jpegData) {
        if (!videoEnabled) return;
        String key = examId + "_" + studentId;
        
        LinkedHashMap<Long, byte[]> buffer = frameBuffer.computeIfAbsent(key, k -> new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > MAX_BUFFER_FRAMES;
            }
        });
        
        buffer.put(System.currentTimeMillis(), jpegData);
    }

    /**
     * 从缓存中获取最近N秒的帧
     */
    public List<byte[]> getRecentFrames(long examId, long studentId, int seconds) {
        String key = examId + "_" + studentId;
        LinkedHashMap<Long, byte[]> buffer = frameBuffer.get(key);
        if (buffer == null || buffer.isEmpty()) return List.of();

        long cutoffTime = System.currentTimeMillis() - seconds * 1000L;
        return buffer.entrySet().stream()
            .filter(e -> e.getKey() >= cutoffTime)
            .map(Map.Entry::getValue)
            .toList();
    }

    /**
     * 生成视频证据（从帧缓存合成短视频）
     */
    @Async("videoProcessExecutor")
    public void generateVideoEvidence(long examId, long studentId, long violationId,
            String description) {
        if (!videoEnabled) return;

        try {
            // 获取最近N秒的帧
            List<byte[]> frames = getRecentFrames(examId, studentId, recordDuration);
            if (frames.size() < 3) {
                log.debug("帧数不足，跳过视频生成: examId={}, studentId={}, frames={}", 
                    examId, studentId, frames.size());
                return;
            }

            // 生成视频到临时目录（FFmpeg需要文件路径）
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("evidence_video_");
            String videoPath = tempDir.resolve(UUID.randomUUID() + ".mp4").toString();

            // 合成视频（使用FFmpeg）
            createVideoFromFrames(frames, videoPath);

            // 读取生成的视频文件数据到内存
            File videoFile = new File(videoPath);
            byte[] videoData = java.nio.file.Files.readAllBytes(videoFile.toPath());

            // 生成缩略图（使用第一帧）
            byte[] thumbnailData = null;
            if (!frames.isEmpty()) {
                thumbnailData = frames.get(0); // 帧本身就是JPEG数据，直接使用
            }

            // 删除临时文件
            videoFile.delete();
            tempDir.toFile().delete();

            // 保存视频记录到数据库
            VideoEvidence evidence = new VideoEvidence();
            evidence.setExamId(examId);
            evidence.setStudentId(studentId);
            evidence.setViolationId(violationId);
            evidence.setEvidenceType("video_clip");
            evidence.setFileData(videoData);
            evidence.setFileSize((long) videoData.length);
            evidence.setDuration(frames.size() / 10); // 假设10fps
            evidence.setThumbnailData(thumbnailData);
            evidence.setCaptureTime(LocalDateTime.now());
            evidence.setDescription(description);
            videoEvidenceMapper.insert(evidence);

            // 更新违规记录关联
            if (violationId > 0) {
                ViolationRecord violation = violationRecordMapper.selectById(violationId);
                if (violation != null) {
                    violation.setVideoEvidenceId(evidence.getId());
                    violationRecordMapper.updateById(violation);
                }
            }

            // 通知监考教师
            wsHandler.broadcastToExamRoom(examId, "video_evidence_ready", Map.of(
                "examId", examId,
                "studentId", studentId,
                "evidenceId", evidence.getId(),
                "hasThumbnail", thumbnailData != null
            ), "teacher");

            log.info("视频证据生成完成: examId={}, studentId={}, evidenceId={}, duration={}s",
                examId, studentId, evidence.getId(), evidence.getDuration());

        } catch (Exception e) {
            log.error("生成视频证据失败: examId={}, studentId={}, error={}",
                examId, studentId, e.getMessage());
        }
    }

    /**
     * 仅保存截图证据（存数据库，不写文件系统）
     */
    public VideoEvidence saveScreenshotEvidence(long examId, long studentId, long violationId,
            byte[] imageData, String description) {
        try {
            VideoEvidence evidence = new VideoEvidence();
            evidence.setExamId(examId);
            evidence.setStudentId(studentId);
            evidence.setViolationId(violationId > 0 ? violationId : null);
            evidence.setEvidenceType("screenshot");
            evidence.setFileData(imageData);
            evidence.setFileSize((long) imageData.length);
            evidence.setCaptureTime(LocalDateTime.now());
            evidence.setDescription(description);
            videoEvidenceMapper.insert(evidence);

            return evidence;
        } catch (Exception e) {
            log.error("保存截图证据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从帧列表合成视频
     */
    private void createVideoFromFrames(List<byte[]> frames, String outputPath) throws Exception {
        // 先将第一帧保存为临时图片，用于获取尺寸
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "exam_evidence_" + System.currentTimeMillis());
        tempDir.mkdirs();

        String firstFramePath = tempDir.getAbsolutePath() + "/frame_000.jpg";
        saveFrameAsImage(frames.get(0), firstFramePath);

        Mat firstFrame = opencv_imgcodecs.imread(firstFramePath);
        int width = firstFrame.cols();
        int height = firstFrame.rows();
        firstFrame.release();

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
            recorder.setFormat("mp4");
            recorder.setFrameRate(10);
            recorder.setVideoBitrate(1500000);
            recorder.setVideoQuality(25);
            recorder.start();

            for (int i = 0; i < frames.size(); i++) {
                String framePath = tempDir.getAbsolutePath() + "/frame_" + 
                    String.format("%03d", i) + ".jpg";
                saveFrameAsImage(frames.get(i), framePath);

                try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(framePath)) {
                    grabber.start();
                    Frame frame = grabber.grabImage();
                    if (frame != null) {
                        recorder.record(frame);
                    }
                    grabber.stop();
                }
            }

            recorder.stop();
        }

        // 清理临时文件
        File[] tempFiles = tempDir.listFiles();
        if (tempFiles != null) {
            for (File f : tempFiles) f.delete();
        }
        tempDir.delete();
    }

    /**
     * 将帧数据保存为图片
     */
    private void saveFrameAsImage(byte[] jpegData, String outputPath) {
        try {
            Mat rawData = new Mat(1, jpegData.length, CV_8UC1, new BytePointer(jpegData));
            Mat frame = opencv_imgcodecs.imdecode(rawData, IMREAD_COLOR);
            rawData.release();

            if (!frame.empty()) {
                new File(outputPath).getParentFile().mkdirs();
                opencv_imgcodecs.imwrite(outputPath, frame);
            }
            frame.release();
        } catch (Exception e) {
            log.debug("保存帧图片失败: {}", e.getMessage());
        }
    }

    /**
     * 获取考试的视频证据列表
     */
    public List<VideoEvidence> getExamEvidence(long examId, Long studentId) {
        LambdaQueryWrapper<VideoEvidence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoEvidence::getExamId, examId);
        wrapper.eq(studentId != null, VideoEvidence::getStudentId, studentId);
        wrapper.orderByDesc(VideoEvidence::getCaptureTime);
        return videoEvidenceMapper.selectList(wrapper);
    }

    /**
     * 清除考试缓存
     */
    public void clearExamBuffer(long examId) {
        frameBuffer.keySet().removeIf(key -> key.startsWith(examId + "_"));
        log.info("已清除考试帧缓存: examId={}", examId);
    }
}
