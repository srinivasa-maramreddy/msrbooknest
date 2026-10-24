#!/bin/sh
set -eu

envsubst '${PORT} ${AUTH_SERVICE_URL} ${CATALOG_SERVICE_URL} ${CART_SERVICE_URL} ${ORDER_SERVICE_URL} ${PAYMENT_SERVICE_URL} ${BUYBACK_SERVICE_URL}' \
  < /etc/nginx/templates/gateway.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
