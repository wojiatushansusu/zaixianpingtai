package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.mapper.UserFaceMapper;
import com.exam.pojo.UserFace;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 人脸比对服务
 * 使用OpenCV进行简单的人脸检测和特征比对
 * 
 * 注意：生产环境建议使用商业人脸识别SDK（如百度AI、腾讯云）提高精度
 */
@Slf4j
@Service
public class FaceCompareService {

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    @Value("${app.face.threshold:0.75}")
    private double faceThreshold;

    @Value("${app.face.enabled:true}")
    private boolean faceEnabled;

    private CascadeClassifier faceClassifier;

    @PostConstruct
    public void init() {
        try {
            String classifierPath = null;
            
            // 1. 尝试从项目根目录的 opencv/ 下加载
            String localPath = System.getProperty("user.dir") + "/opencv/haarcascade_frontalface_alt2.xml";
            if (new File(localPath).exists()) {
                classifierPath = localPath;
            }
            
            // 2. 尝试从 classpath (resources/opencv/) 加载
            if (classifierPath == null) {
                try (InputStream is = getClass().getClassLoader()
                        .getResourceAsStream("opencv/haarcascade_frontalface_alt2.xml")) {
                    if (is != null) {
                        // 将classpath资源提取到临时文件
                        java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "opencv");
                        Files.createDirectories(tempDir);
                        java.nio.file.Path tempFile = tempDir.resolve("haarcascade_frontalface_alt2.xml");
                        Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        classifierPath = tempFile.toString();
                    }
                }
            }

            if (classifierPath != null && new File(classifierPath).exists()) {
                faceClassifier = new CascadeClassifier(classifierPath);
                if (!faceClassifier.empty()) {
                    log.info("OpenCV人脸检测器加载成功: {}", classifierPath);
                } else {
                    log.warn("人脸检测器加载后为空，将使用简化比对模式");
                    faceClassifier = null;
                }
            } else {
                log.warn("人脸检测器文件未找到(classpath: opencv/haarcascade_frontalface_alt2.xml)，将使用简化比对模式");
            }
        } catch (Exception | LinkageError e) {
            // UnsatisfiedLinkError / NoClassDefFoundError 继承自 Error 而非 Exception，
            // 只写 catch (Exception) 抓不住它们。而这里在 @PostConstruct 里，
            // 异常一旦冒出去，Spring 上下文启动失败，登录、出卷、题库、错题本全部一起完蛋。
            // 人脸核验只是监考的一项可选能力，缺 OpenCV 原生库时应该降级，不该致命。
            log.warn("人脸识别初始化失败，已降级为简化比对模式: {}", e.toString());
            faceClassifier = null;
        }
    }

    /**
     * 比对两张图片中的人脸
     * 
     * @param photoPath1 注册照片路径
     * @param photoPath2 核验照片路径
     * @return 相似度得分 (0.0 ~ 1.0)，越接近1越相似
     */
    public double compare(String photoPath1, String photoPath2) {
        try {
            // 确保使用绝对路径
            String absPath1 = toAbsolutePath(photoPath1);
            String absPath2 = toAbsolutePath(photoPath2);
            
            Mat img1 = imread(absPath1, IMREAD_GRAYSCALE);
            Mat img2 = imread(absPath2, IMREAD_GRAYSCALE);

            if (img1.empty() || img2.empty()) {
                log.warn("图片读取失败: {} -> {} 或 {} -> {}", photoPath1, absPath1, photoPath2, absPath2);
                return 0.0;
            }

            // 检测人脸区域
            Rect[] faces1 = detectFaces(img1);
            Rect[] faces2 = detectFaces(img2);

            if (faces1.length == 0 || faces2.length == 0) {
                log.warn("未检测到人脸");
                return 0.0;
            }

            // 提取人脸区域并调整到统一大小
            Mat face1 = extractFace(img1, faces1[0], new Size(100, 100));
            Mat face2 = extractFace(img2, faces2[0], new Size(100, 100));

            if (face1.empty() || face2.empty()) {
                return 0.0;
            }

            // 方法1：结构相似度 (SSIM简化版 - 像素差异比对)
            Mat diff = new Mat();
            absdiff(face1, face2, diff);
            Scalar mean = mean(diff);
            double pixelDiff = mean.get(0) / 255.0;

            // 转换为相似度 (差异越小，相似度越高)
            double similarity = 1.0 - Math.min(pixelDiff * 2.5, 1.0);

            // 清理
            img1.release();
            img2.release();
            face1.release();
            face2.release();
            diff.release();

            return Math.max(0, Math.min(1, similarity));

        } catch (Exception e) {
            log.error("人脸比对异常: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 检测图片中的人脸（多尺度策略，提升远距离小脸检出率）
     */
    private Rect[] detectFaces(Mat grayImg) {
        RectVector faces = new RectVector();
        if (faceClassifier != null && !faceClassifier.empty()) {
            // 标准检测
            faceClassifier.detectMultiScale(grayImg, faces, 1.05, 3, 0, new Size(30, 30), new Size(0, 0));
            // 标准检测失败，降低门槛重试
            if (faces.size() == 0) {
                faceClassifier.detectMultiScale(grayImg, faces, 1.05, 2, 0, new Size(15, 15), new Size(0, 0));
            }
            Rect[] result = new Rect[(int) faces.size()];
            for (int i = 0; i < faces.size(); i++) {
                result[i] = faces.get(i);
            }
            return result;
        }

        // P2: 如果没有分类器，返回空数组（上层调用者会看到faces.length==0返回0.0）
        return new Rect[0];
    }

    /**
     * 提取人脸区域并调整大小
     */
    private Mat extractFace(Mat src, Rect faceRect, Size targetSize) {
        Mat face = new Mat(src, faceRect);
        Mat resized = new Mat();
        resize(face, resized, targetSize);
        face.release();
        return resized;
    }

    /**
     * 保存上传的图片文件
     */
    public String savePhoto(byte[] imageData, String subDir) {
        try {
            // 防止路径遍历攻击：subDir不允许包含上级目录引用
            if (subDir == null || subDir.contains("..") || subDir.contains("\0")) {
                throw new IllegalArgumentException("非法的子目录路径");
            }
            // 只允许字母数字和斜杠/反斜杠
            String sanitizedDir = subDir.replaceAll("[^a-zA-Z0-9_/\\\\-]", "");

            String dir = uploadBasePath + "/" + sanitizedDir;
            Files.createDirectories(Paths.get(dir));
            
            String fileName = UUID.randomUUID().toString() + ".jpg";
            java.nio.file.Path filePath = java.nio.file.Paths.get(dir).toAbsolutePath().normalize().resolve(fileName);

            // 二次校验：确保最终路径在uploadBasePath下
            java.nio.file.Path baseDir = java.nio.file.Paths.get(uploadBasePath).toAbsolutePath().normalize();
            if (!filePath.startsWith(baseDir)) {
                throw new IllegalArgumentException("文件保存路径超出允许范围");
            }
            
            Files.write(filePath, imageData);
            
            return filePath.toString().replace("\\", "/");
        } catch (IllegalArgumentException e) {
            log.error("保存图片失败(非法路径): {}", e.getMessage());
            throw new RuntimeException("非法的文件路径");
        } catch (Exception e) {
            log.error("保存图片失败: {}", e.getMessage());
            throw new RuntimeException("图片保存失败");
        }
    }

    /**
     * 比两张图片中的人脸（基于byte数组，不写临时文件）
     */
    public double compareBytes(byte[] photoData1, byte[] photoData2) {
        try {
            // P2: 使用UUID确保临时目录唯一性，避免并发竞态
            java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "face_compare_" + UUID.randomUUID().toString());
            java.nio.file.Files.createDirectories(tempDir);

            String path1 = tempDir.resolve("p1.jpg").toString();
            String path2 = tempDir.resolve("p2.jpg").toString();
            java.nio.file.Files.write(java.nio.file.Paths.get(path1), photoData1);
            java.nio.file.Files.write(java.nio.file.Paths.get(path2), photoData2);

            double result = compare(path1, path2);

            // 清理临时文件
            new File(path1).delete();
            new File(path2).delete();
            tempDir.toFile().delete();

            return result;
        } catch (Exception e) {
            log.error("字节比对异常: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 比较一张路径图片和一张byte数组图片
     */
    public double compare(String photoPath, byte[] photoData2) {
        try {
            java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "face_compare_" + UUID.randomUUID().toString());
            java.nio.file.Files.createDirectories(tempDir);
            String path2 = tempDir.resolve("p2.jpg").toString();
            java.nio.file.Files.write(java.nio.file.Paths.get(path2), photoData2);
            
            double result = compare(photoPath, path2);

            new File(path2).delete();
            tempDir.toFile().delete();

            return result;
        } catch (Exception e) {
            log.error("混合比对异常: {}", e.getMessage());
            return 0.0;
        }
    }

    public boolean isFaceEnabled() {
        return faceEnabled;
    }

    public double getFaceThreshold() {
        return faceThreshold;
    }

    /**
     * 将路径转换为绝对路径
     */
    private String toAbsolutePath(String path) {
        if (path == null) return null;
        java.nio.file.Path p = java.nio.file.Paths.get(path);
        if (p.isAbsolute()) {
            return path;
        }
        return Paths.get(System.getProperty("user.dir"), path).toString();
    }
}
