# 人脸模型权重（可选，自托管用）

前端加载 face-api 模型时**先找本地 `/models/`，找不到再回落到 jsdelivr CDN**。
默认目录是空的，也就是走 CDN——多数情况够用。

如果部署环境访问 CDN 不稳定（部分网络下 jsdelivr 会超时），把权重放进来即可离线运行：

```bash
# 三个网络都需要：检测、关键点、特征
npx --yes @vladmandic/face-api@1.7.12 --help 2>/dev/null || true

# 或者直接从 npm 包解出来
npm pack @vladmandic/face-api@1.7.12
tar -xzf vladmandic-face-api-1.7.12.tgz
cp package/model/* frontend/public/models/
```

需要的文件（共约 12 MB）：

```
ssd_mobilenetv1_model-weights_manifest.json + shard1 + shard2
face_landmark_68_model-weights_manifest.json + shard1
face_recognition_model-weights_manifest.json + shard1 + shard2
```

> 这些二进制不建议提交进仓库：会让 clone 体积每次翻倍增长。
> 部署时按上面的命令拉一次即可，`public/models/` 已在 `.gitignore` 里忽略。
