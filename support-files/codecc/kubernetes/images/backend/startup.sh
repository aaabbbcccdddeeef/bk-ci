#! /bin/sh

mkdir -p $CODECC_LOGS_DIR
chmod 777 $CODECC_LOGS_DIR

java -server \
     -Dsun.jnu.encoding=UTF-8 \
     -Dfile.encoding=UTF-8 \
     -Xloggc:$CODECC_LOGS_DIR/gc.log \
     -XX:+PrintTenuringDistribution \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=oom.hprof \
     -XX:ErrorFile=$CODECC_LOGS_DIR/error_sys.log \
     -Dspring.profiles.active=$CODECC_PROFILE \
     -Dserver.fullname=$SERVER_FULLNAME \
     -Dserver.prefix=$SERVICE_PREFIX \
     -Dserver.common.name=$SERVER_COMMON_NAME \
     -Dlogging.file.path=$CODECC_LOGS_DIR \
     $CODECC_JVM_OPTION \
     -jar /data/workspace/app.jar
