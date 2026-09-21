FROM eclipse-temurin:17-jdk
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
WORKDIR /workspace
COPY ./*.jar /workspace/app.jar
ENTRYPOINT ["java","-Xmx256m","-jar","app.jar"]