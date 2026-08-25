#!/bin/sh
# ==============================================================================
# 通用 Spring Boot jar 服务控制脚本（启动 / 停止 / 重启 / 状态 / 日志）
#
# 设计原则：路径、jar 名、端口、JRE 全部可配置，默认“复制到 jar 同级目录即用”，
#           无需修改本文件任何内容即可用于任意 jar 部署。
#
# 用法（在脚本所在目录执行）：
#   ./jar-control.sh start     启动应用
#   ./jar-control.sh stop      停止应用（先优雅关闭，超时则强杀）
#   ./jar-control.sh restart   重启应用
#   ./jar-control.sh status    查看运行状态
#   ./jar-control.sh logs      实时跟踪控制台输出（Ctrl+C 退出）
#
# 配置加载：启动时会从「同级 config/ 目录」读取 application.yml 及激活的
#           profile 文件（application-${profile}.yml），并把 logback 配置指向
#           config/logback-spring.xml。
#
# 可覆盖的环境变量（均非必填，不设置则自动推断）：
#   APP_HOME   应用（jar）所在目录，默认 = 本脚本自身所在目录（不写死）
#   APP_JAR    具体 jar 文件名；不填则自动取目录下第一个 *.jar（不写死）
#   JAVA_HOME  指定 JRE/JDK 目录，优先级最高；否则自动探测常见路径 / 系统 PATH
#   APP_PORT   应用监听端口，仅用于 start 时可选开放防火墙；不填则尝试从
#              config/application.yml（及激活 profile 文件）解析 server.port
#   OPEN_PORT  yes/no，start 时是否开放防火墙端口，默认 no（幂等）
#   JVM_OPTS   额外 JVM 参数，如 "-Xms256m -Xmx1024m"
#   LIB_DIR    外部依赖 jar 所在目录，默认 = 本目录下的 lib/（不写死）；
#              目录下 *.jar 经 -Dloader.path 注入 classpath（fat jar 自带依赖仍生效）
# ==============================================================================

set -u

# ------------------------- 基础路径（默认取脚本自身所在目录） -------------------
# 关键：不写死任何部署路径——脚本复制到哪个目录，就以哪个目录为 APP_HOME
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
APP_HOME="${APP_HOME:-$SCRIPT_DIR}"
PID_FILE="$APP_HOME/app.pid"
LOG_DIR="$APP_HOME/logs"
CONSOLE_OUT="$LOG_DIR/console.out"   # 进程 stdout/stderr 落盘（业务日志由 logback 写）
CFG_DIR="$APP_HOME/config"
APP_PORT="${APP_PORT:-}"
OPEN_PORT="${OPEN_PORT:-no}"
LIB_DIR="${LIB_DIR:-$APP_HOME/lib}"   # 外部依赖 jar 目录，默认 jar 同级 lib/

# ------------------------- JRE 路径解析（多级兜底，不写死） ---------------------
# 优先级：环境变量 JAVA_HOME > 默认 /usr/ssa/jre-17 > 常见路径 > 系统 PATH
resolve_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME"
    return 0
  fi
  for dir in /usr/ssa/jre-17 /usr/ssa/jre /usr/lib/jvm/*/jre /usr/lib/jvm/* /opt/java; do
    if [ -x "$dir/bin/java" ]; then
      echo "$dir"
      return 0
    fi
  done
  java_bin=$(command -v java 2>/dev/null)
  if [ -n "$java_bin" ]; then
    echo "$(dirname "$(dirname "$java_bin")")"
    return 0
  fi
  return 1
}

JAVA_HOME=$(resolve_java)
if [ $? -ne 0 ]; then
  echo "ERROR: 未找到可用的 JRE/JDK。请设置环境变量 JAVA_HOME，或安装到默认路径 /usr/ssa/jre-17" >&2
  exit 1
fi
export JAVA_HOME
PATH="$JAVA_HOME/bin:$PATH"

# ------------------------- 定位 jar 文件（不写死文件名） -----------------------
if [ -z "${APP_JAR:-}" ]; then
  for f in "$APP_HOME"/*.jar; do
    [ -f "$f" ] && { APP_JAR=$(basename "$f"); break; }
  done
fi
JAR_FILE="$APP_HOME/$APP_JAR"
if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: 在 $APP_HOME 下未找到可启动的 jar 文件" >&2
  exit 1
fi

# ------------------------- 从 config 解析 server.port -------------------------
# 兼容两种写法：扁平 "server.port: 8080" 与嵌套 "server:\n  port: 8080"
parse_server_port() {
  [ -f "$1" ] || return 0
  awk '
    /^[[:space:]]*server[[:space:]]*\.[[:space:]]*port[[:space:]]*:/ {
      match($0, /[0-9]+/); if (RSTART>0){print substr($0,RSTART,RLENGTH); exit}
    }
    /^[[:space:]]*server:[[:space:]]*$/ { in_server=1; next }
    in_server && /^[[:space:]]+port[[:space:]]*:[[:space:]]*[0-9]+/ {
      match($0, /[0-9]+/); print substr($0,RSTART,RLENGTH); exit
    }
    in_server && /^[[:space:]]*[A-Za-z]/ && !/^[[:space:]]*port/ { in_server=0 }
  ' "$1" 2>/dev/null
}

parse_active_profile() {
  [ -f "$1" ] || return 0
  awk '
    # 扁平写法：spring.profiles.active: dev
    /^[[:space:]]*spring[[:space:]]*\.[[:space:]]*profiles[[:space:]]*\.[[:space:]]*active[[:space:]]*:/ {
      v=$0; sub(/^[^:]*:/, "", v); gsub(/[[:space:]]/, "", v); sub(/#.*/, "", v); print v; exit
    }
    # 嵌套写法：profiles:\n  active: dev
    /^[[:space:]]*profiles:[[:space:]]*$/ { in_profiles=1; next }
    in_profiles && /^[[:space:]]+active[[:space:]]*:[[:space:]]*/ {
      v=$0; sub(/^[^:]*:/, "", v); gsub(/[[:space:]]/, "", v); sub(/#.*/, "", v); print v; exit
    }
    in_profiles && /^[[:space:]]*[A-Za-z]/ && !/^[[:space:]]*active/ { in_profiles=0 }
  ' "$1" 2>/dev/null
}

resolve_port_from_config() {
  [ -d "$CFG_DIR" ] || return 0
  active=$(parse_active_profile "$CFG_DIR/application.yml")
  for f in "$CFG_DIR/application.yml" "$CFG_DIR/application-$active.yml"; do
    [ -f "$f" ] || continue
    p=$(parse_server_port "$f")
    [ -n "$p" ] && { echo "$p"; return 0; }
  done
  return 0
}

if [ -z "$APP_PORT" ]; then
  APP_PORT=$(resolve_port_from_config)
fi

# ------------------------- 辅助函数 --------------------------------------------
is_running() {
  [ -f "$PID_FILE" ] || return 1
  pid=$(cat "$PID_FILE" 2>/dev/null)
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

ensure_dirs() {
  [ -d "$LOG_DIR" ] || mkdir -p "$LOG_DIR"
}

open_port() {
  [ "$OPEN_PORT" = "yes" ] || return 0
  [ -n "$APP_PORT" ] || { echo "未解析到端口，跳过防火墙开放"; return 0; }
  if command -v iptables >/dev/null 2>&1; then
    if ! iptables -C INPUT -p tcp --dport "$APP_PORT" -j ACCEPT 2>/dev/null; then
      iptables -I INPUT -p tcp --dport "$APP_PORT" -j ACCEPT
      echo "已开放防火墙端口 $APP_PORT"
    else
      echo "防火墙端口 $APP_PORT 已开放，跳过"
    fi
  fi
}

# ------------------------- 启动 ------------------------------------------------
start_app() {
  if is_running; then
    echo "应用已在运行 (PID=$(cat "$PID_FILE"))，无需重复启动。"
    return 0
  fi
  ensure_dirs
  cd "$APP_HOME"   # 让 logback 中相对路径 ./logs 稳定解析为 jar 同级目录
  echo "Starting $APP_JAR ..."
  echo "  JAVA_HOME        = $JAVA_HOME"
  echo "  JAR_FILE         = $JAR_FILE"
  echo "  CONFIG_DIR       = $CFG_DIR"
  echo "  LIB_DIR          = $LIB_DIR（外部 jar，-Dloader.path 注入）"
  echo "  LOGBACK_CONFIG   = $CFG_DIR/logback-spring.xml"
  echo "  APP_PORT         = ${APP_PORT:-<未解析>}"
  echo "  控制台输出        = $CONSOLE_OUT（业务日志由 logback 按自身配置落盘）"
  # 外部依赖：PropertiesLauncher 通过 -Dloader.path 注入额外 jar（fat jar 自带 BOOT-INF/lib 仍生效）
  LOADER_OPT=""
  if [ -d "$LIB_DIR" ]; then
    LOADER_OPT="-Dloader.path=$LIB_DIR/"
  fi
  nohup java ${JVM_OPTS:-} ${LOADER_OPT} \
    -Dlogging.config=file:"$CFG_DIR/logback-spring.xml" \
    -jar "$JAR_FILE" \
    --spring.config.additional-location=file:"$CFG_DIR/" \
    >>"$CONSOLE_OUT" 2>&1 &
  echo $! > "$PID_FILE"
  # 等待进程拉起
  sleep 3
  if is_running; then
    echo "应用已启动 (PID=$(cat "$PID_FILE"))"
    open_port
  else
    echo "ERROR: 启动失败，请查看控制台输出：$CONSOLE_OUT" >&2
    rm -f "$PID_FILE"
    exit 1
  fi
}

# ------------------------- 停止 ------------------------------------------------
stop_app() {
  if ! is_running; then
    echo "应用未运行。"
    rm -f "$PID_FILE"
    return 0
  fi
  pid=$(cat "$PID_FILE")
  echo "Stopping $APP_JAR (PID=$pid) ..."
  kill "$pid" 2>/dev/null
  # 等待优雅关闭，最多 30s
  i=0
  while kill -0 "$pid" 2>/dev/null && [ "$i" -lt 30 ]; do
    i=$((i + 1))
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "优雅关闭超时，强制终止 (PID=$pid) ..."
    kill -9 "$pid" 2>/dev/null
    sleep 1
  fi
  rm -f "$PID_FILE"
  echo "应用已停止。"
}

# ------------------------- 状态 ------------------------------------------------
status_app() {
  if is_running; then
    echo "$APP_JAR 运行中 (PID=$(cat "$PID_FILE"))，端口 ${APP_PORT:-<未解析>}"
  else
    echo "$APP_JAR 未运行"
  fi
}

# ------------------------- 主分发 ----------------------------------------------
case "${1:-}" in
  start)   start_app ;;
  stop)    stop_app ;;
  restart) stop_app; start_app ;;
  status)  status_app ;;
  logs)    if [ -f "$CONSOLE_OUT" ]; then tail -f "$CONSOLE_OUT"; else echo "暂无控制台输出文件：$CONSOLE_OUT"; fi ;;
  *) echo "Usage: $0 {start|stop|restart|status|logs}" >&2; exit 1 ;;
esac

exit 0
