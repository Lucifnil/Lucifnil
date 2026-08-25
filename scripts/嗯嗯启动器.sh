#!/system/bin/sh

# This script must live in /data/adb. It accepts the selected direct child
# filename from the app so multiple .so files in that directory are supported.
set -u

TARGET_DIR=/data/adb

die() {
    printf '%s\n' "启动失败：$*" >&2
    exit 1
}

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd -P) || die "无法确定脚本目录"
[ "$script_dir" = "$TARGET_DIR" ] || die "脚本必须位于 $TARGET_DIR"

card_key=""
selected_name=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        -k)
            [ "$#" -ge 2 ] || die "-k 缺少卡密"
            card_key=$2
            shift 2
            ;;
        -s|--so)
            [ "$#" -ge 2 ] || die "-s 缺少 .so 文件名"
            selected_name=$2
            shift 2
            ;;
        --)
            shift
            break
            ;;
        *)
            die "未知参数：$1"
            ;;
    esac
done

if [ -z "$card_key" ]; then
    printf '请输入卡密: '
    IFS= read -r card_key || true
fi

[ -n "$card_key" ] || die "卡密不能为空"

if [ -n "$selected_name" ]; then
    case "$selected_name" in
        */*|.|..)
            die "无效的 .so 文件名"
            ;;
        *.so)
            ;;
        *)
            die "请选择以 .so 结尾的文件"
            ;;
    esac

    so_file="$TARGET_DIR/$selected_name"
    # -f intentionally follows a direct symlink. Some modules distribute a
    # .so link here; the visible entry is still selected from /data/adb only.
    [ -f "$so_file" ] || die "所选文件不存在或不是普通文件：$selected_name"
else
    # Backward-compatible fallback for terminal usage: only choose
    # automatically when there is exactly one direct .so file.
    found_count=0
    so_file=""
    for candidate in "$TARGET_DIR"/*.so; do
        [ -f "$candidate" ] || continue
        so_file=$candidate
        found_count=$((found_count + 1))
    done

    case "$found_count" in
        0)
            die "在 $TARGET_DIR 中没有找到 .so 文件"
            ;;
        1)
            ;;
        *)
            die "找到多个 .so 文件，请在应用设置中选择要启动的文件"
            ;;
    esac
fi

chmod 700 "$so_file" || die "无法为 $so_file 设置执行权限"
exec "$so_file" -k "$card_key" --record-mirror
