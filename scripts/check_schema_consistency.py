#!/usr/bin/env python3
"""
实体字段 vs 建表脚本 一致性检查。

起因：一次部署里，4 个坑有 3 个是同一类——实体加了字段，建表脚本忘了改列。
这类问题在开发期不报错（本地库可能是老结构或手改过），只在别人部署时炸，
而且报的是 Unknown column，看着像 SQL 写错，其实是表结构对不上。

检查方式：解析所有带 @TableName 的实体（排除 @TableField(exist = false)），
把字段名转成下划线，和 schema.sql 里 CREATE TABLE 的列做比对。

退出码：发现问题返回 1，CI 据此失败。
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA = os.path.join(ROOT, "backend/src/main/resources/db/schema.sql")
SRC = os.path.join(ROOT, "backend/src/main/java")


def parse_schema(path):
    """返回 {表名: {列名}}"""
    text = io.open(path, encoding="utf-8").read()
    tables = {}
    for m in re.finditer(r"CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE", text, re.S):
        name, body = m.group(1), m.group(2)
        cols = set()
        for line in body.split("\n"):
            col = re.match(r"\s*`(\w+)`\s", line)
            if col:
                cols.add(col.group(1))
        tables[name] = cols
    return tables


def camel_to_snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def parse_entities(src_dir):
    """返回 {表名: (文件名, {列名})}"""
    entities = {}
    for root, _, files in os.walk(src_dir):
        for filename in files:
            if not filename.endswith(".java"):
                continue
            path = os.path.join(root, filename)
            source = io.open(path, encoding="utf-8").read()
            match = re.search(r'@TableName\("(\w+)"\)', source)
            if not match:
                continue
            table = match.group(1)

            # 标记为不存在于表的字段（通常是联表查询用的展示字段）先剔除，
            # 否则会被误判成缺列。
            body = re.sub(
                r"@TableField\(\s*exist\s*=\s*false\s*\)\s*private\s+\S+\s+\w+\s*;",
                "",
                source,
            )
            fields = re.findall(
                r"private\s+(?!static)(?!final)[\w<>,\s\[\]]+?\s+(\w+)\s*;", body
            )
            entities[table] = (filename, {camel_to_snake(f) for f in fields})
    return entities


def main():
    tables = parse_schema(SCHEMA)
    entities = parse_entities(SRC)

    print("实体 %d 个，建表语句 %d 张" % (len(entities), len(tables)))

    failures = []
    for table, (filename, columns) in sorted(entities.items()):
        if table not in tables:
            failures.append((table, filename, ["<整张表在建表脚本里不存在>"]))
            continue
        missing = sorted(columns - tables[table])
        if missing:
            failures.append((table, filename, missing))

    print()
    print("=" * 62)
    if failures:
        print("发现 %d 张表与实体不一致：" % len(failures))
        print()
        for table, filename, missing in failures:
            print("  %s  （%s）" % (table, filename))
            for col in missing:
                print("      缺少列: %s" % col)
        print()
        print("修法：")
        print("  1. 在 schema.sql 对应的 CREATE TABLE 里补上这些列（新库才能建对）")
        print("  2. 另加一个 migration-*.sql，用 ALTER TABLE 补（老库才能升级）")
        print()
        print("只改建表脚本不够：已经存在的库不会重新执行 schema.sql。")
        return 1

    print("实体与建表脚本一致，没有缺列。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
