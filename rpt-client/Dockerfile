# Dockerfile for rpt-client
# 1. mvn clean package -Dmaven.test.skip=true
# 2. Build with: docker build -f Dockerfile -t rpt-client .
# 3. Run with: docker run -d -v /home/rpt/conf:/home/rpt/conf --restart=always --name rpt-client rpt-client

FROM openjdk:8-jre
MAINTAINER Lynn <lynn@promptness.cn>

ENV TIME_ZONE=PRC
RUN ln -snf /usr/share/zoneinfo/$TIME_ZONE /etc/localtime && echo $TIME_ZONE > /etc/timezone

ADD target/rpt-client-*.jar /home/rpt/rpt-client.jar

WORKDIR /home/rpt

ENV VIRTUAL="-XX:+UseG1GC \
             -XX:MaxGCPauseMillis=200"

ENV OPTION="-Dnetworkaddress.cache.ttl=600 \
            -Djava.security.egd=file:/dev/./urandom \
            -Djava.awt.headless=true \
            -Duser.timezone=Asia/Shanghai \
            -Dclient.encoding.override=UTF-8 \
            -Dfile.encoding=UTF-8 \
            -Xbootclasspath/a:./conf"

ENTRYPOINT ["sh","-c","java -server -d64 $VIRTUAL $OPTION -jar rpt-client.jar"]