#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 质量门禁：检查 JaCoCo 分支覆盖率和 PITest 变异得分。"""
import argparse
import sys
import xml.etree.ElementTree as ET

TARGET_CLASS = "org/linlinjava/litemall/db/service/CouponVerifyService"


def check_jacoco(path, minimum):
    root = ET.parse(path).getroot()
    covered = missed = 0
    for cls in root.iter("class"):
        if cls.get("name") != TARGET_CLASS:
            continue
        for counter in cls.findall("counter"):
            if counter.get("type") == "BRANCH":
                covered += int(counter.get("covered"))
                missed += int(counter.get("missed"))
    total = covered + missed
    if total == 0:
        print("FAIL: JaCoCo 中没有找到 %s 的分支数据" % TARGET_CLASS)
        return False
    rate = covered / total
    print("branch coverage: %d/%d = %.1f%% (gate %.0f%%)" %
          (covered, total, rate * 100, minimum * 100))
    return rate >= minimum


def check_pit(path, minimum):
    root = ET.parse(path).getroot()
    total = killed = 0
    for mutation in root.findall("mutation"):
        total += 1
        if mutation.get("status") == "KILLED":
            killed += 1
    if total == 0:
        print("FAIL: PIT 报告中没有变异体")
        return False
    rate = killed / total
    print("mutation score: %d/%d = %.1f%% (gate %.0f%%)" %
          (killed, total, rate * 100, minimum * 100))
    return rate >= minimum


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jacoco")
    parser.add_argument("--pit")
    parser.add_argument("--min-branch", type=float, default=0.85)
    parser.add_argument("--min-mutation", type=float, default=0.85)
    args = parser.parse_args()

    ok = True
    if args.jacoco:
        ok = check_jacoco(args.jacoco, args.min_branch) and ok
    if args.pit:
        ok = check_pit(args.pit, args.min_mutation) and ok
    if not args.jacoco and not args.pit:
        print("用法: quality_gate.py --jacoco <xml> 或 --pit <xml>")
        return 2
    print("GATE PASSED" if ok else "GATE FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
