FROM node:24-alpine@sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runs nginx as an unprivileged user, which cannot bind ports below 1024, hence 8080.
FROM nginxinc/nginx-unprivileged:1.29-alpine@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6
# The entrypoint fills in the template at startup. It writes to /tmp, the only writable place in the deployment,
# where the container's file system is read-only; nginx.conf includes the result from there. The default for
# REAL_IP_FROM trusts no proxy.
ENV NGINX_ENVSUBST_OUTPUT_DIR=/tmp \
    NGINX_ENVSUBST_FILTER=^REAL_IP_FROM$ \
    REAL_IP_FROM=127.0.0.1
RUN rm /etc/nginx/conf.d/default.conf \
    && sed -i 's|include /etc/nginx/conf.d/\*.conf;|include /tmp/default.conf;|' /etc/nginx/nginx.conf
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
