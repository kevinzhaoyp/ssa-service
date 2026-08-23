#!/bin/sh
#这是一个通用的jar启动sh文件,无需改这里的内容,直接复制到jar所在路径即可

#使用方法：
#启动应用：./jar-control.sh start
#停止应用：./jar-control.sh stop
#重启应用：./jar-control.sh restart
#给脚本执行权限： chmod +x jar-control.sh
# 指定JDK路径
JAVA_HOME=/var/yp/jre-17
if [ ! -d "$JAVA_HOME" ] ; then
  echo "JAVA_HOME 未设置"
  exit -1
fi
export JAVA_HOME
PATH=$JAVA_HOME/bin:$PATH

# 找出当前目录下的jar文件
cur_dir=$(cd `dirname $0`; pwd)
SERVICE_FILENAME=`find $cur_dir -name  "*.jar" -print`
if [ ! -f "$SERVICE_FILENAME" ]
then
  echo -e "$SERVICE_HOME 不存在.jar文件\n"
  exit -1
fi
echo ""
echo JAVA_HOME=$JAVA_HOME
echo SERVICE_FILENAME=$SERVICE_FILENAME
echo ""
# 启动
start_app() {
  echo "Starting the application..."
  nohup java -Dloader.path=plugins/lib/ -jar "$SERVICE_FILENAME" >app.out 2>&1 &
  # 开启端口
  iptables -I INPUT -p tcp --dport 9913 -j ACCEPT
  echo "Application started. See logs at $LOG_HOME/app.out"
}
# 停止
stop_app() {
  echo "Stopping the application..."
  # 这里假设你的应用支持接收SIGTERM信号优雅关闭
  PID=$(pgrep -f "$SERVICE_FILENAME")
  if [ -n "$PID" ]; then
    kill "$PID"
    echo "Application stopped."
  else
    echo "No running instance of the application found."
  fi
}

case "$1" in
  start)
    start_app
    ;;
  stop)
    stop_app
    ;;
  restart)
    stop_app
    sleep 2
    start_app
    ;;
  *)
    echo "Usage: $0 {start|stop|restart}"
    exit 1
    ;;
esac

exit 0