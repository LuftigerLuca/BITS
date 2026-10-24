FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runs nginx as an unprivileged user, which cannot bind ports below 1024, hence 8080.
FROM nginxinc/nginx-unprivileged:1.29-alpine
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
