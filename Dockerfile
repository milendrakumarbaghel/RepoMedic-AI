# The multi-platform manifest digests pin both runtimes, not just floating tags.
FROM node:23.10.0-bookworm-slim@sha256:b89d748ea010f4d276c9d45c750fa5f371cef3fcc7486f739f07e5aad1b998a8 AS node-runtime
FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS toolchain
RUN apt-get update && apt-get install -y --no-install-recommends make curl unzip ca-certificates libstdc++6 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=node-runtime /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
WORKDIR /workspace

FROM toolchain AS build
COPY . .
RUN make setup
# Prove the full test/build steps require no network after dependency hydration.
RUN --network=none make test && make build && make smoke

FROM build AS test
CMD ["make", "test"]

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS runtime
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
RUN groupadd --gid 10001 repomedic && useradd --uid 10001 --gid 10001 --no-create-home repomedic \
    && mkdir -p /app/.repomedic && chown -R 10001:10001 /app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar ./repomedic.jar
ENV PORT=8080 BIND_ADDRESS=0.0.0.0 REPOMEDIC_DATA_DIR=.repomedic REPOMEDIC_SANDBOX_ENABLED=false
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+process.env.PORT+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
ENTRYPOINT ["java", "-jar", "repomedic.jar"]
