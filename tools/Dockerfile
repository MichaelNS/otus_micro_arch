FROM debian:stretch-slim as packager

ENV JDK_VERSION="17.0.5.8.1"
ENV JDK_URL="https://corretto.aws/downloads/resources/${JDK_VERSION}/amazon-corretto-${JDK_VERSION}-linux-x64.tar.gz"
ENV JDK_ARJ_FILE="openjdk-${JDK_VERSION}.tar.gz"

# target JDK installation names
ENV OPT="/opt"
ENV JKD_DIR_NAME="amazon-corretto-${JDK_VERSION}-linux-x64"
ENV JAVA_HOME="${OPT}/${JKD_DIR_NAME}"
ENV JAVA_MINIMAL="${OPT}/java-minimal"

# required for strip-debug to work
# RUN apk add --no-cache binutils
RUN apt-get update
RUN apt-get -y install binutils
# RUN echo $(ls /usr/bin/objcopy)

# downlodad JDK to the local file
ADD "$JDK_URL" "$JDK_ARJ_FILE"
# COPY amazon-corretto-17.0.5.8.1-linux-x64.tar.gz "$JDK_ARJ_FILE"

# extract JDK and add to PATH
RUN mkdir -p "$OPT" && \
    tar xf "$JDK_ARJ_FILE" -C "$OPT" ;

ENV PATH="$PATH:$JAVA_HOME/bin"

# build modules distribution
# jdk/jfr - akka (java.lang.NoClassDefFoundError: jdk/jfr/Event)
RUN jlink \
    --verbose \
    --add-modules \
        java.base,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,jdk.httpserver,java.instrument,jdk.unsupported,jdk.jfr \
    --compress 2 \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output "$JAVA_MINIMAL"

FROM debian:stretch-slim
ENV JAVA_HOME=/opt/java-minimal
ENV PATH="$PATH:$JAVA_HOME/bin"
COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"

# https://github.com/maslick/minimalka
# +
# https://blog.monosoul.dev/2022/04/25/reduce-java-docker-image-size/
# java.base,java.management,java.naming,java.net.http,java.security.jgss,java.security.sasl,java.sql,jdk.httpserver,jdk.unsupported 
