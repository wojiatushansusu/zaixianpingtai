#!/usr/bin/env python3
"""
环境变量「三方对账」：应用引用的 ↔ docker-compose 传的 ↔ .env.example 写的。

起因：`app.ai.enabled` 曾写死 false，没有环境变量入口。部署方按文档填了 AI_API_KEY，
功能却依然是哑的——**不报错，只是没生效**。这类问题在代码里看不出来，
只有把「谁引用了什么、谁传了什么」摆在一起才暴露。

「引用」包括两条路径，少扫一条就会误报：
  - application.yml 里的 ${NAME:默认值}
  - Java 里的 @Value("${NAME...}") / System.getenv("NAME") / environment.getProperty("NAME")

检查三件事：
  1. 应用引用了，但 compose 没传     → 部署时静默走默认值（最危险的一类）
  2. compose 传了，但应用从不引用     → 名字写错或已成死配置
  3. compose 从 .env 取值，但 .env.example 没写 → 部署方不知道要填

退出码：1 表示有问题。
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_YML = os.path.join(ROOT, "backend/src/main/resources/application.yml")
COMPOSE = os.path.join(ROOT, "docker-compose.yml")
ENV_EXAMPLE = os.path.join(ROOT, ".env.example")
JAVA_SRC = os.path.join(ROOT, "backend/src/main/java")

# 由运行时/操作系统消费，不经过 Spring 配置
RUNTIME_ALLOWLIST = {
    "TZ",
    "LANG",
    "JAVA_TOOL_OPTIONS",
    "JAVA_OPTS",
    "JAVA_HOME",
}


def read(path):
    """读文件并归一化换行。

    仓库在 Windows 上可能被检出为 CRLF；解析逻辑都按 LF 写，
    不归一化会出现「文件明明在、却匹配不到」的假故障。
    """
    text = io.open(path, encoding="utf-8").read()
    return text.replace("\r\n", "\n").replace("\r", "\n")


ENV_NAME = re.compile(r"^[A-Z][A-Z0-9_]*$")


def is_env_name(name):
    """项目的环境变量全用大写+下划线；小写的是 Spring 属性路径，不是环境变量。"""
    return bool(ENV_NAME.match(name))


def yml_placeholders(text):
    """application.yml 里 ${NAME} / ${NAME:default} 引用到的环境变量名"""
    found = re.findall(r"\$\{([A-Za-z_][A-Za-z0-9_]*)", text)
    return {name for name in found if is_env_name(name)}


def java_references(src_dir):
    """Java 源码直接读取的环境变量名（绕过 Spring 配置的那些）"""
    names = set()
    patterns = [
        r'@Value\(\s*"\$\{([A-Za-z_][A-Za-z0-9_]*)',
        r'System\.getenv\(\s*"([A-Za-z_][A-Za-z0-9_]*)"',
        r'getProperty\(\s*"([A-Za-z_][A-Za-z0-9_]*)"',
    ]
    for root, _, files in os.walk(src_dir):
        for filename in files:
            if not filename.endswith(".java"):
                continue
            text = read(os.path.join(root, filename))
            for pattern in patterns:
                # 只要环境变量：@Value("${app.upload.path}") 里抓到的是 Spring 属性路径，
                # 它由 application.yml 提供，不该拿去和 compose 对账
                names.update(n for n in re.findall(pattern, text) if is_env_name(n))
    return names


def compose_backend_env(text):
    """取 backend 服务的 environment：{变量名: 原始值}。

    逐行扫缩进，不做多行正则——compose 嵌套层级多，正则很容易在
    「哪个键属于哪个块」上出错，缩进判断则是确定的。
    """
    env = {}
    in_backend = False
    in_env = False

    for raw in text.split("\n"):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue

        indent = len(raw) - len(raw.lstrip())
        stripped = raw.strip()

        # services 之下、2 空格缩进且以冒号结尾的行 = 服务名
        if indent == 2 and stripped.endswith(":"):
            in_backend = stripped[:-1] == "backend"
            in_env = False
            continue

        if not in_backend:
            continue

        # backend 块内 4 空格缩进的键
        if indent == 4 and stripped.endswith(":"):
            in_env = stripped == "environment:"
            continue

        if in_env:
            # 字典写法 AI_API_KEY: ${AI_API_KEY:-} ／ 列表写法 - AI_API_KEY=${...}
            match = re.match(r"-?\s*([A-Za-z_][A-Za-z0-9_]*)\s*[:=]\s*(.*)$", stripped)
            if match:
                env[match.group(1)] = match.group(2).strip()

    return env


def compose_required_vars(text):
    """compose 里用 ${VAR:?错误提示} 强制要求的变量"""
    return set(re.findall(r"\$\{([A-Za-z_][A-Za-z0-9_]*):\?", text))


def env_example_keys(text):
    keys = set()
    for raw in text.split("\n"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\s*=", line)
        if match:
            keys.add(match.group(1))
    return keys


def main():
    referenced = yml_placeholders(read(APP_YML)) | java_references(JAVA_SRC)
    env = compose_backend_env(read(COMPOSE))
    passed = set(env)
    required = compose_required_vars(read(COMPOSE))
    documented = env_example_keys(read(ENV_EXAMPLE))

    print("应用引用 %d 个，compose 传给 backend %d 个，.env.example 记录 %d 个"
          % (len(referenced), len(passed), len(documented)))

    silent = referenced - passed - RUNTIME_ALLOWLIST
    dead = passed - referenced - RUNTIME_ALLOWLIST

    # 只有「值来自 .env」（写成 ${...}）的变量才需要文档；
    # 写死字面量的（如 REDIS_HOST: redis）本来就不该让部署方去填。
    from_env = {name for name, value in env.items() if "${" in value}
    undocumented = (from_env | required) - documented - RUNTIME_ALLOWLIST

    problems = 0

    if silent:
        problems += 1
        print()
        print("[1] 应用引用了，但 compose 没传 —— 部署时会静默使用默认值")
        for name in sorted(silent):
            print("      %s" % name)
        print("      这类最危险：功能不报错，只是没生效。")

    if dead:
        problems += 1
        print()
        print("[2] compose 传了，但应用从不引用 —— 名字写错或已成死配置")
        for name in sorted(dead):
            print("      %s" % name)

    if undocumented:
        problems += 1
        print()
        print("[3] compose 从 .env 取值，但 .env.example 没写 —— 部署方不知道要填")
        for name in sorted(undocumented):
            print("      %s" % name)

    print()
    print("=" * 62)
    if problems:
        print("发现 %d 类问题。" % problems)
        print("修法：变量要么在 application.yml 用 ${NAME:默认值} 引用（或在 Java 里读），")
        print("     要么在 docker-compose.yml 的 backend.environment 里传下去，")
        print("     并在 .env.example 里留一行说明。")
        return 1

    print("环境变量三方一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
