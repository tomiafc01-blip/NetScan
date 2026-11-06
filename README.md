# NetScan

Escáner de red Wi-Fi para Android que identifica los dispositivos conectados a **tu propia red**, con el modelo/fabricante más preciso que cada aparato permita averiguar.

> Herramienta de **diagnóstico**. Úsala solo en redes tuyas o con permiso. No rompe contraseñas ni accede a redes ajenas.

## Qué hace

Combina 5 capas de identificación para sacar el máximo de cada dispositivo:

1. **Barrido de hosts** — recorre el subred (ping + sondeo TCP) para ver qué IPs están vivas.
2. **mDNS / Bonjour** (`NsdManager`) — Chromecast, Apple TV, impresoras IPP, Sonos… muchos sueltan nombre y modelo.
3. **SSDP / UPnP** — TVs, consolas y routers publican un XML con `modelName` y `manufacturer` exactos.
4. **NetBIOS** (UDP 137) — nombre de equipo de las PCs con Windows.
5. **Banners HTTP + puertos** — la interfaz web de routers, cámaras y NAS delata el modelo.
6. **OUI del MAC** — fabricante por los primeros 3 bytes del MAC, como respaldo.

Fusiona todo y muestra cada dispositivo con su mejor etiqueta, tipo, puertos abiertos y qué capa aportó cada dato.

## Límites honestos

- En **Android 10+** leer la tabla ARP del sistema está restringido sin root, así que el **MAC de otros equipos** no siempre se consigue. Las demás capas compensan.
- Los celulares modernos usan **MAC aleatoria**; cuando se detecta, la app lo indica en vez de inventar un fabricante.
- Un smart TV, impresora o Chromecast suelen dar modelo exacto; una laptop o un celular con MAC aleatoria, mucho menos.

## Permisos

- `ACCESS_FINE_LOCATION`: obligatorio desde Android 8 para leer el nombre de la red (SSID). Se pide al abrir la app.

## Compilar

El APK se genera solo con **GitHub Actions** (`.github/workflows/build.yml`) en cada push a `main`:

1. Crea el repo `tomiafc01-blip/NetScan` en GitHub.
2. Sube estos archivos (sin la carpeta `build/`).
3. Entra a la pestaña **Actions** → espera a que termine **Build APK**.
4. Descarga el artefacto **NetScan-debug** → dentro está el `.apk`.
5. Instálalo en tu POCO X7 Pro (activa «instalar apps de origen desconocido»).

No necesitas Android Studio ni el `gradle-wrapper.jar`: el workflow instala Gradle 8.9 y genera el wrapper solo.

## Ampliar la base de fabricantes

`app/src/main/assets/oui.txt` trae una lista curada. Puedes pegarle más entradas de la base oficial de IEEE en el mismo formato `AABBCC=Nombre`.
