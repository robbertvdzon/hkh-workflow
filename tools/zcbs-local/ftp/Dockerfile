FROM alpine:3.20
RUN apk add --no-cache vsftpd \
 && adduser -D -h /home/hkhadmin -u 1001 hkhadmin \
 && echo 'hkhadmin:hkhtest' | chpasswd
COPY vsftpd.conf /etc/vsftpd/vsftpd.conf
EXPOSE 21 30000-30009
CMD ["sh", "-c", "touch /var/log/vsftpd.log; tail -F /var/log/vsftpd.log & exec /usr/sbin/vsftpd /etc/vsftpd/vsftpd.conf"]
