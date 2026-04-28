# Dockerfile for rpt-server
# 1. mvn clean package -Dmaven.test.skip=true
# 2. Build with: docker build -f Dockerfile -t rpt-server .
# 3. Run with:
#    docker run -d --network host -v /opt/rpt/conf:/home/rpt/conf --restart=always --name rpt-server rpt-server
#    Or expose specific ports:
#    docker run -d -p 6167:6167 -p 80:80 -p 443:443 -p 8000:8000 -p 4000-9999:4000-9999 -v /opt/rpt/conf:/home/rpt/conf --restart=always --name rpt-server rpt-server

FROM eclipse-temurin:8-jre
LABEL authors="lynn"

ENV TIME_ZONE=PRC
RUN ln -snf /usr/share/zoneinfo/"$TIME_ZONE" /etc/localtime && echo "$TIME_ZONE" > /etc/timezone

ADD target/rpt-server-*.jar /home/rpt/rpt-server.jar

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

ENTRYPOINT ["sh","-c","java -server -d64 $VIRTUAL $OPTION -jar rpt-server.jar"]

