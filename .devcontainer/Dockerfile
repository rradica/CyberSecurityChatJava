FROM maven:3.9.9-eclipse-temurin-21

RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl unzip bash-completion procps \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspaces/CyberSecurityChatJava