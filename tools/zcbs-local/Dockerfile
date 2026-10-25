FROM httpd:2.4
RUN apt-get update \
 && apt-get install -y --no-install-recommends perl libwww-perl \
 && rm -rf /var/lib/apt/lists/*
COPY apache.conf /usr/local/apache2/conf/httpd.conf
COPY overrides/fake-sendmail.sh /usr/sbin/sendmail
RUN chmod +x /usr/sbin/sendmail
