# OpenCV.js（可选，人脸核验用）

人脸核验需要 `opencv.js`。**平台不在打开页面时加载它**，只有用户点开人脸核验时才去
`/opencv/opencv.js` 取一次；取不到就直接降级（不做人脸检测，其余功能不受影响）。

## 为什么不再从 CDN 加载

之前是 `<script src="https://docs.opencv.org/4.9.0/opencv.js">` 写死在入口 HTML 里，
两个问题：

1. 这个文件 **10 MB**，每次打开页面都会下载——哪怕根本不用人脸核验
2. `docs.opencv.org` 在境内经常不通或极慢，等于每次访问都白等一次

## 想启用的话

一条命令：

```bash
bash scripts/fetch-opencv.sh
```

脚本会下载、校验体积与 SHA-256、写入本目录。已存在且完整时直接跳过，
文件损坏（例如上次下载中断）会自动重新下载。

放好后重新构建镜像生效：

```bash
docker compose up -d --build
```

**没有接入 `npm run build`**：构建一旦依赖外网下载，网络一抖整个镜像就构建失败，
而这只是个可选功能。所以保持手动一步，失败也不影响主流程。

文件不进仓库（已在 `.gitignore` 里忽略），避免每次 clone 多拉 10 MB。
