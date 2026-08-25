#!/bin/sh
set -eu

ssl_certificate_path=/data/easymedia/webrtc.pem
if [ ! -s /run/secrets/tls_fullchain ] || [ ! -s /run/secrets/tls_private_key ]; then
    echo "TLS certificate secrets are missing" >&2
    exit 1
fi
umask 077
{
    cat /run/secrets/tls_private_key
    cat /run/secrets/tls_fullchain
} > "$ssl_certificate_path"
chown easymedia:easymedia "$ssl_certificate_path"
chmod 0600 "$ssl_certificate_path"
export SIMPLE_SECRET_ZLM4J_SSL_CERTIFICATE_PATH="$ssl_certificate_path"

public_ip="${WEBRTC_PUBLIC_IP:-}"
if [ -z "$public_ip" ]; then
    if [ -z "${DOMAIN:-}" ]; then
        echo "DOMAIN or WEBRTC_PUBLIC_IP must be configured" >&2
        exit 1
    fi
    public_ip="$(getent ahostsv4 "$DOMAIN" | awk 'NR == 1 { print $1 }')"
fi

if [ -z "$public_ip" ]; then
    echo "Unable to resolve an IPv4 address for DOMAIN=$DOMAIN" >&2
    exit 1
fi

export SIMPLE_SECRET_ZLM4J_RTC_HOST="$public_ip"

echo "Starting EasyMedia with WebRTC public IP $public_ip" >&2
exec gosu easymedia java \
    -Djna.library.path=/opt/zlmediakit/lib \
    -jar /opt/easymedia/application.jar
