#!/bin/bash

# GitHub推送脚本
# 使用方法: ./PUSH_TO_GITHUB.sh

set -e

USERNAME="Akon1993"
REPO_NAME="java-performance-optimization-sop"

echo "========================================"
echo "  GitHub推送脚本"
echo "  用户名: $USERNAME"
echo "  仓库名: $REPO_NAME"
echo "========================================"
echo ""

# 检查是否已配置远程仓库
if git remote -v | grep -q origin; then
    echo "远程仓库已配置，更新URL..."
    git remote set-url origin "https://github.com/$USERNAME/$REPO_NAME.git"
else
    echo "配置远程仓库..."
    git remote add origin "https://github.com/$USERNAME/$REPO_NAME.git"
fi

echo ""
echo "远程仓库信息:"
git remote -v
echo ""

# 推送代码
echo "正在推送到GitHub..."
echo "注意: 推送时会要求输入GitHub用户名和个人访问令牌(PAT)"
echo ""

if git push -u origin master; then
    echo ""
    echo "✅ 推送成功!"
    echo ""
    echo "项目地址: https://github.com/$USERNAME/$REPO_NAME"
    echo ""
else
    echo ""
    echo "❌ 推送失败"
    echo ""
    echo "可能的原因:"
    echo "1. GitHub仓库不存在 - 请先访问 https://github.com/new 创建仓库"
    echo "2. 认证失败 - 请确保使用正确的GitHub Token"
    echo "3. 网络问题 - 请检查网络连接"
    echo ""
    echo "创建仓库步骤:"
    echo "1. 访问 https://github.com/new"
    echo "2. 输入仓库名: $REPO_NAME"
    echo "3. 选择 Public 或 Private"
    echo "4. 不要勾选 'Initialize this repository with a README'"
    echo "5. 点击 Create repository"
    echo ""
    echo "获取GitHub Token:"
    echo "1. 访问 https://github.com/settings/tokens"
    echo "2. 点击 Generate new token (classic)"
    echo "3. 选择 repo 权限"
    echo "4. 生成后复制token作为密码使用"
fi
