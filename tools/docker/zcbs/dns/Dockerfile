FROM alpine:3.22
RUN apk add --no-cache dnsmasq
# Elke naam wijst naar DNS_ANSWER en elke vraag wordt gelogd (docker logs zcbs-dns).
# Geen doorverwijzing naar echte DNS (--no-resolv): clients die deze server gebruiken kunnen niets echts opzoeken.
ENTRYPOINT ["sh", "-c", "exec dnsmasq -k --no-daemon --log-queries --log-facility=- --no-resolv --no-hosts --interface=eth0 --local=/#/ --address=/#/${DNS_ANSWER}"]
