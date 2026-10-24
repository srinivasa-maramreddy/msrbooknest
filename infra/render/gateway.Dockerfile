FROM nginx:1.29-alpine

RUN apk add --no-cache gettext
COPY infra/render/nginx.conf.template /etc/nginx/templates/gateway.conf.template
COPY infra/render/start-gateway.sh /usr/local/bin/start-gateway

EXPOSE 10000
ENTRYPOINT ["sh", "/usr/local/bin/start-gateway"]
