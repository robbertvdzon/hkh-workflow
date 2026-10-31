http://localhost:8081/cgi-bin/archief.pl
http://localhost:8081/cgi-bin/archief.pl?user=reset


Deze files van de server:
/cgi-bin                : archief.pl , objecten.pl
/cgi-bin/misc           : cgi-lib.pl , httpbl.pm
/cgi-bin/archief        : archief.txt, updates.txt
/cgi-bin/archief/misc   : cfg-cbs.pl , layout.html

Deze files zelf uit de stubs naar cgi-bin/misc/ :
stubs/stubs/permit.lst 
stubs/sendmail.pm 

Docker starten en stoppen:
docker compose up --build -d
docker compose down

In de pod:
/home/hkhadmin/domains/historischekringheemskerk.nl/public_html
chmod -R a+rwX *
chmod a+x cgi-bin/*.pl

Gebruiker:
user1 / abc

FTP:
host: 127.0.0.1
poort: 2121
username: hkhadmin
passwd: hkhtest

SFT:
host: 127.0.0.1
poort: 2222
username: hkhadmin
passwd: hkhtest


Echte images.exe op een testcomputer (zie ../../windows-testlaptop/README.md):
FTP staat ook op de standaardpoort 21 (naast 2121), met de gebruiker die het echte images.exe gebruikt:
username: ZCBS@historischekringheemskerk.nl
passwd: hkhtest
(thuismap een niveau boven public_html, want images.exe gebruikt paden als /public_html/archief/small/)
Zet op de testcomputer in het hosts-bestand: <IP van deze Mac>  ftp.historischekringheemskerk.nl (en www.zcbs.nl).
Alleen om een onbekende hostnaam te vinden is er een DNS-server die elke naam naar de Mac laat wijzen en alles logt:
docker compose --profile dns up -d      # queries in: docker logs zcbs-dns
docker stop zcbs-dns                    # weer uit (niet "docker compose down": dat stopt ook de zcbs-container)
