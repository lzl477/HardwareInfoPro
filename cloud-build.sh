#!/bin/bash
# =================================================================
# HardwareInfoPro 一键云端构建脚本
# 用法: 在能访问 GitHub 的网络环境下运行此脚本
# =================================================================

set -e

echo "=========================================="
echo "  HardwareInfoPro 云端构建工具"
echo "=========================================="

# 配置
REPO="lzl477/HardwareInfoPro"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

# 检查 git
if ! command -v git &> /dev/null; then
    echo "错误: 未找到 git，请先安装 git"
    exit 1
fi

cd "$PROJECT_DIR"

# 方法1: 通过 git push (需要 GitHub 登录)
push_with_git() {
    echo ""
    echo "[方法1] 通过 git push 推送代码..."
    
    # 确保 remote 已设置
    git remote set-url origin "https://github.com/$REPO.git" 2>/dev/null || \
        git remote add origin "https://github.com/$REPO.git"
    
    # 推送
    if git push -u origin main; then
        echo "代码推送成功!"
        return 0
    else
        echo "git push 失败"
        return 1
    fi
}

# 方法2: 通过 GitHub API (需要 Token)
push_with_api() {
    echo ""
    echo "[方法2] 通过 GitHub API 上传文件..."
    
    if [ -z "$GITHUB_TOKEN" ]; then
        echo "错误: 需要 GitHub Token"
        echo "请设置环境变量: export GITHUB_TOKEN=your_token_here"
        echo "Token 可在 https://github.com/settings/tokens/new 创建 (勾选 repo 权限)"
        return 1
    fi
    
    FILES=$(git ls-files)
    TOTAL=$(echo "$FILES" | wc -l)
    SUCCESS=0
    
    for file in $FILES; do
        CONTENT=$(base64 -w 0 "$file" 2>/dev/null || base64 "$file")
        
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
            -X PUT \
            -H "Authorization: token $GITHUB_TOKEN" \
            -H "Accept: application/vnd.github.v3+json" \
            -d "{\"message\":\"Add $file\",\"content\":\"$CONTENT\"}" \
            "https://api.github.com/repos/$REPO/contents/$file")
        
        if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
            SUCCESS=$((SUCCESS + 1))
            echo "[$SUCCESS/$TOTAL] 已上传: $file"
        else
            echo "[$((SUCCESS + 1))/$TOTAL] 失败: $file (HTTP $HTTP_CODE)"
        fi
        
        sleep 0.3
    done
    
    echo ""
    echo "上传完成: 成功 $SUCCESS / $TOTAL 个文件"
    return 0
}

# 监控构建状态
monitor_build() {
    echo ""
    echo "=========================================="
    echo "监控 GitHub Actions 构建状态..."
    echo "=========================================="
    echo "构建页面: https://github.com/$REPO/actions"
    echo ""
    
    if [ -n "$GITHUB_TOKEN" ]; then
        echo "等待构建启动..."
        sleep 10
        
        for i in $(seq 1 30); do
            STATUS=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
                "https://api.github.com/repos/$REPO/actions/runs" | \
                python3 -c "import sys,json; runs=json.load(sys.stdin).get('workflow_runs',[]); print(runs[0]['status'] if runs else 'none')" 2>/dev/null || echo "error")
            
            CONCLUSION=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
                "https://api.github.com/repos/$REPO/actions/runs" | \
                python3 -c "import sys,json; runs=json.load(sys.stdin).get('workflow_runs',[]); print(runs[0].get('conclusion','') if runs else '')" 2>/dev/null || echo "")
            
            echo "构建状态: $STATUS, 结果: $CONCLUSION"
            
            if [ "$STATUS" = "completed" ]; then
                if [ "$CONCLUSION" = "success" ]; then
                    echo ""
                    echo "=========================================="
                    echo "构建成功! APK 已生成!"
                    echo "=========================================="
                    
                    # 获取 artifact 下载链接
                    ARTIFACT_URL=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
                        "https://api.github.com/repos/$REPO/actions/runs" | \
                        python3 -c "import sys,json; runs=json.load(sys.stdin)['workflow_runs']; print(runs[0]['artifacts_url'])" 2>/dev/null)
                    
                    if [ -n "$ARTIFACT_URL" ]; then
                        DOWNLOAD_URL=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
                            "$ARTIFACT_URL" | \
                            python3 -c "import sys,json; arts=json.load(sys.stdin)['artifacts']; print(arts[0]['archive_download_url'] if arts else '')" 2>/dev/null)
                        
                        if [ -n "$DOWNLOAD_URL" ]; then
                            echo "正在下载 APK..."
                            curl -L -H "Authorization: token $GITHUB_TOKEN" \
                                -o "HardwareInfoPro.apk.zip" "$DOWNLOAD_URL"
                            echo "APK 已下载: HardwareInfoPro.apk.zip"
                        fi
                    fi
                else
                    echo "构建失败: $CONCLUSION"
                    echo "请查看日志: https://github.com/$REPO/actions"
                fi
                return
            fi
            
            sleep 30
        done
    else
        echo "未提供 Token，无法自动监控构建。"
        echo "请手动查看: https://github.com/$REPO/actions"
    fi
}

# 主流程
echo ""
echo "选择推送方式:"
echo "  1) git push (推荐，需要在终端中登录 GitHub)"
echo "  2) GitHub API (需要 Personal Access Token)"
echo ""
read -p "请选择 [1/2]: " choice

case $choice in
    1)
        push_with_git
        ;;
    2)
        push_with_api
        ;;
    *)
        echo "无效选择"
        exit 1
        ;;
esac

if [ $? -eq 0 ]; then
    monitor_build
fi
