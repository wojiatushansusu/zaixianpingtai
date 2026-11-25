# OpenCV.js（可选，人脸核验用）

人脸核验需要 `opencv.js`。**平台不在打开页面时加载它**，只有用户点开人脸核验时才去
`/opencv/opencv.js` 取一次；取不到就直接降级（不做人脸检测，其余功能不受影响）。

## 为什么不再从 CDN 加载

之前是 `<script src="https://docs.opencv.org/4.9.0/opencv.js">` 写死在入口 HTML 里，
两个问题：

1. 这个文件 **10 MB**，每次打开页面都会下载——哪怕根本不用人脸核验
2. `docs.opencv.org` 在境内经常不通或极慢，等于每次访问都白等一次

## 想启用的话

把官方发行包里的 `opencv.js` 放到这个目录（约 10 MB）：

```bash
# 方式一：从官方 release 取
curl -LO https://github.com/opencv/opencv/releases/download/4.9.0/opencv-4.9.0-docs.zip
# 解压后取 opencv.js，放到 public/opencv/

# 方式二：本地已有构建产物，直接复制
cp /path/to/opencv.js frontend/public/opencv/
```

放好后重新构建镜像即可。文件不进仓库（已在 `.gitignore` 里忽略），
避免每次 clone 多拉 10 MB。
