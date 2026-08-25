# syntax=docker/dockerfile:1.7

FROM ubuntu:22.04 AS zlm-build

ARG ZLM_REF=fdaec2604d4091886158cbaca39dca2b5e5d25db

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        cmake \
        git \
        libssl-dev \
        libsrtp2-dev \
        pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src/ZLMediaKit
RUN git init \
    && git remote add origin https://github.com/ZLMediaKit/ZLMediaKit.git \
    && git fetch --depth 1 origin "${ZLM_REF}" \
    && git checkout --detach FETCH_HEAD \
    && git submodule update --init --recursive

RUN cmake -S . -B build \
        -DCMAKE_BUILD_TYPE=Release \
        -DENABLE_API=ON \
        -DENABLE_TESTS=OFF \
        -DENABLE_WEBRTC=ON \
    && cmake --build build --parallel "$(nproc)" \
    && mkdir -p /opt/zlmediakit/lib \
    && find release/linux/Release -maxdepth 1 \
        \( -type f -o -type l \) \
        \( -name '*.so' -o -name '*.so.*' \) \
        -exec cp -a '{}' /opt/zlmediakit/lib/ \; \
    && test -f /opt/zlmediakit/lib/libmk_api.so \
    && ! ldd /opt/zlmediakit/lib/libmk_api.so | grep -q 'not found'

FROM maven:3.9.9-eclipse-temurin-17 AS app-build

WORKDIR /workspace
COPY . .
RUN mvn -B -ntp \
    -pl simple-secret-application/simple-secret-application-easymedia-test \
    -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gosu \
        libssl3 \
        libsrtp2-1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system easymedia \
    && useradd --system --gid easymedia --home-dir /opt/easymedia easymedia \
    && mkdir -p /opt/easymedia /data/easymedia \
    && chown -R easymedia:easymedia /opt/easymedia /data/easymedia

COPY --from=zlm-build /opt/zlmediakit/lib/ /opt/zlmediakit/lib/
COPY --from=app-build \
    /workspace/simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar \
    /opt/easymedia/application.jar
COPY --chown=easymedia:easymedia \
    simple-secret-application/simple-secret-application-easymedia-test/docker/entrypoint.sh \
    /opt/easymedia/entrypoint.sh

RUN chmod 0755 /opt/easymedia/entrypoint.sh

ENV LD_LIBRARY_PATH=/opt/zlmediakit/lib \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom" \
    TZ=Asia/Shanghai

WORKDIR /opt/easymedia

EXPOSE 9878 8000/tcp 8000/udp

ENTRYPOINT ["/opt/easymedia/entrypoint.sh"]
