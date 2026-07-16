# Rootless/OpenShift-friendly PostgreSQL base image
FROM registry.access.redhat.com/ubi9/ubi-minimal:latest

# Build-time as root only
USER 0

# Install PostgreSQL server packages from UBI/AppStream
# (package names may vary slightly by tag; this works on current UBI9 streams)
RUN microdnf -y update && \
    microdnf -y install \
      postgresql-server \
      postgresql \
      shadow-utils \
      findutils \
      hostname \
      tar \
      gzip && \
    microdnf clean all

# Create writable locations for arbitrary OpenShift UID
ENV APP_ROOT=/opt/app-root \
    PGDATA=/opt/app-root/src/postgresql/data \
    PATH=/usr/bin:$PATH

RUN mkdir -p /opt/app-root/src/postgresql /docker-entrypoint-initdb.d && \
    chgrp -R 0 /opt/app-root /docker-entrypoint-initdb.d && \
    chmod -R g=u /opt/app-root /docker-entrypoint-initdb.d

# Copy entrypoint
COPY run-postgresql.sh /usr/local/bin/run-postgresql.sh
RUN chmod 0755 /usr/local/bin/run-postgresql.sh && \
    chgrp 0 /usr/local/bin/run-postgresql.sh && \
    chmod g=u /usr/local/bin/run-postgresql.sh

EXPOSE 5432

# OpenShift will inject random non-root UID; this keeps image compliant
USER 1001

ENTRYPOINT ["/usr/local/bin/run-postgresql.sh"]