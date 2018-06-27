FROM alpine:latest as builder
WORKDIR /build

ARG REPO=camunda-optimize
ARG VERSION=2.0.0
ARG SNAPSHOT=false
ARG DISTRO=standalone

# Nexus credentials
ARG USERNAME
ARG PASSWORD

# Download Optimize
RUN apk add --no-cache tar wget
COPY Dockerfile distro/target/*-${DISTRO}.tar.gz /tmp/
COPY docker/download.sh /bin/
RUN /bin/download.sh

############ Production image ###############
FROM openjdk:8u151-jre-alpine3.7

ENV OPTIMIZE_HOME=/optimize
ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"
ENV OPTIMIZE_CLASSPATH=${OPTIMIZE_HOME}/environment:${OPTIMIZE_HOME}/plugin/*:${OPTIMIZE_HOME}/*
ENV WAIT_FOR=
ENV WAIT_FOR_TIMEOUT=30
ENV TZ=Europe/Berlin

WORKDIR ${OPTIMIZE_HOME}

EXPOSE 8090 8091

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./optimize.sh"]

RUN apk add --no-cache bash curl tini tzdata && \
    addgroup -S optimize && \
    adduser -S -g optimize optimize && \
    chown optimize:optimize /optimize

COPY --chown=optimize:optimize --from=builder /build .
COPY docker/bin/optimize.sh ./optimize.sh
COPY docker/bin/wait-for-it.sh /usr/local/bin/wait-for-it.sh

USER optimize
