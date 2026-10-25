# ── 构建阶段 ──
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
# 有 lock 文件走 npm ci（可复现），没有则退回 install
RUN npm ci || npm install

COPY . .
RUN npm run build

# ── 运行阶段 ──
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
