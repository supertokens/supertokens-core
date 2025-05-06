FROM ubuntu:22.04 AS tmp

RUN apt-get update && apt-get upgrade -y
RUN apt-get install build-essential -y
RUN apt-get install -y git-core wget unzip jq curl

# Install OpenJDK 21.0.7
RUN wget https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz

RUN mv OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz /usr/java
RUN mkdir -p /usr/java/
RUN cd /usr/java && tar -xzvf OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz
RUN mv /usr/java/jdk-21.0.7+6 /usr/java/jdk-21.0.7

RUN echo 'JAVA_HOME=/usr/java/jdk-21.0.7' >> /etc/profile
RUN echo 'PATH=$PATH:$HOME/bin:$JAVA_HOME/bin' >> /etc/profile
RUN echo 'export JAVA_HOME' >> /etc/profile
RUN echo 'export JRE_HOME' >> /etc/profile
RUN echo 'export PATH' >> /etc/profile

RUN update-alternatives --install "/usr/bin/java" "java" "/usr/java/jdk-21.0.7/bin/java" 1
RUN update-alternatives --install "/usr/bin/javac" "javac" "/usr/java/jdk-21.0.7/bin/javac" 1

RUN wget -O docker-entrypoint.sh https://raw.githubusercontent.com/supertokens/supertokens-docker-postgresql/master/docker-entrypoint.sh

# RUN wget https://services.gradle.org/distributions/gradle-7.0-all.zip
# RUN unzip gradle-7.0-all.zip
# ENV GRADLE_HOME=/gradle-7.0
# ENV PATH=$PATH:$GRADLE_HOME/bin

RUN git clone https://github.com/supertokens/supertokens-root.git
WORKDIR /supertokens-root
COPY ./pluginInterfaceSupported.json pluginInterfaceSupported.json
RUN git clone --single-branch --branch "$(cat pluginInterfaceSupported.json | jq '.versions[-1]' | tr -d '"')" "https://github.com/supertokens/supertokens-plugin-interface.git"
RUN mkdir -p supertokens-core
COPY ./ supertokens-core
RUN echo "org.gradle.vfs.watch=false" >> ./gradle.properties
RUN ./loadModules
RUN ./utils/setupTestEnv --local


FROM debian:bookworm-slim
RUN groupadd supertokens && useradd -m -s /bin/bash -g supertokens supertokens
RUN apt-get update && apt-get install -y --no-install-recommends gnupg dirmngr curl unzip && rm -rf /var/lib/apt/lists/*
ENV GOSU_VERSION=1.7
RUN set -x \
	&& apt-get update && apt-get install -y --no-install-recommends ca-certificates wget && rm -rf /var/lib/apt/lists/* \
	&& wget -O /usr/local/bin/gosu "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture)" \
	&& wget -O /usr/local/bin/gosu.asc "https://github.com/tianon/gosu/releases/download/$GOSU_VERSION/gosu-$(dpkg --print-architecture).asc" \
	&& export GNUPGHOME="$(mktemp -d)" \
	&& gpg --batch --keyserver hkps://keys.openpgp.org --recv-keys B42F6819007F00F88E364FD4036A9C25BF357DD4 \
	&& gpg --batch --verify /usr/local/bin/gosu.asc /usr/local/bin/gosu \
	&& gpgconf --kill all \
	&& rm -rf "$GNUPGHOME" /usr/local/bin/gosu.asc \
	&& chmod +x /usr/local/bin/gosu \
    && wget -O jre.zip "https://raw.githubusercontent.com/supertokens/jre/master/jre-21.0.7-linux.zip" \
    && mkdir -p /usr/lib/supertokens/jre \
    && unzip jre.zip \
    && mv jre-*/* /usr/lib/supertokens/jre \
    && apt-get purge -y --auto-remove ca-certificates wget unzip \
    && rm -rf jre.zip
COPY --from=tmp --chown=supertokens /supertokens-root/core /usr/lib/supertokens/core
COPY --from=tmp --chown=supertokens /supertokens-root/plugin-interface /usr/lib/supertokens/plugin-interface
COPY --from=tmp --chown=supertokens /supertokens-root/ee /usr/lib/supertokens/ee
COPY --from=tmp --chown=supertokens /supertokens-root/temp/config.yaml /usr/lib/supertokens/config.yaml
COPY --from=tmp --chown=supertokens /supertokens-root/version.yaml /usr/lib/supertokens/version.yaml
COPY --from=tmp --chown=supertokens /docker-entrypoint.sh /usr/local/bin/
RUN mkdir -p /lib/supertokens
RUN chown -R supertokens:supertokens /lib/supertokens
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
RUN echo "$(md5sum /usr/lib/supertokens/config.yaml | awk '{ print $1 }')" >> /CONFIG_HASH
RUN ln -s /usr/local/bin/docker-entrypoint.sh /entrypoint.sh # backwards compat
EXPOSE 3567
USER "supertokens"
CMD ["/usr/lib/supertokens/jre/bin/java", "-classpath", "/usr/lib/supertokens/core/*:/usr/lib/supertokens/plugin-interface/*:/usr/lib/supertokens/ee/*", "io.supertokens.Main", "/usr/lib/supertokens", "DEV", "host=0.0.0.0", "test_mode", "tempDirLocation=/usr/lib/supertokens/temp", "configFile=/usr/lib/supertokens/temp/config.yaml"]
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]