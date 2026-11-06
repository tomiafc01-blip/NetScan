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

Extras de identificación:

- **MAC por NetBIOS**: las PCs con Windows devuelven su MAC real en la respuesta NBSTAT, así el OUI sí funciona con ellas.
- **Modelos de Apple traducidos**: `iPhone14,5` → «iPhone 13», `Mac14,2` → «MacBook Air M2», etc.
- **Nivel de confianza** en cada aparato: 🟢 alta (el aparato publicó su modelo), 🟡 media (nombre o fabricante real), ⚪ baja (suposición por puertos).

## Funciones

- **Detalle por dispositivo**: toca una tarjeta para ver todo lo que se sabe (MAC, fabricante, modelo, puertos con nombre, servicios mDNS, latencia, primera vez visto).
- **Nombres personalizados**: renombra «Samsung TV» como «TV sala»; el nombre se guarda por red.
- **Historial**: pestaña con todos los aparatos que se han conectado a cada red, cuándo se vieron por primera y última vez, y los últimos escaneos.
- **Alerta de dispositivo nuevo**: aviso en pantalla tras cada escaneo y, si activas **Vigilar mi red**, notificación en segundo plano (escaneo cada 30 min con Wi-Fi).
- **Ping**: 10 paquetes con mínimo, promedio, máximo y pérdida, más un veredicto en lenguaje claro.
- **Wake-on-LAN**: enciende PCs que lo tengan activado (necesita conocer su MAC).
- **Abrir interfaz web** del router, cámara o NAS, y copiar IP/MAC.
- **Exportar** el escaneo a CSV (se abre en Excel o Sheets) desde el botón compartir.

## Seguridad y diagnóstico

- **Revisión de riesgos** por aparato: avisa de Telnet, ADB, RDP, FTP o cámaras expuestas, y de si el panel del router va sin cifrar, con una explicación de qué hacer. (No prueba contraseñas: solo mira puertos y da recomendaciones.)
- **Aparatos de confianza**: márcalos como tuyos y dejan de generar alertas; los no confiables se resaltan.
- **Base de fabricantes completa**: más de 31 000 marcas (datos públicos de IEEE), en vez de una lista corta.
- **Escaneo de puertos** 1–1024 bajo demanda desde el detalle de cada aparato.
- **Otras subredes**: escanea la red de invitados o la del repetidor desde el icono de red.
- **Pestaña Wi-Fi**: prueba de velocidad (bajada/subida/latencia), analizador de canales con recomendación en 2.4 GHz, y mapa de señal por cuarto.
- **Widget** de inicio con el número de aparatos del último escaneo.
- **Actividad por aparato**: cuándo se ha conectado en los escaneos recientes.
- **Búsqueda, filtros y orden** por IP, nombre o confianza.

## Límites honestos

- En **Android 10+** leer la tabla ARP del sistema está restringido sin root, así que el **MAC de otros equipos** no siempre se consigue. Las demás capas compensan.
- Los celulares modernos usan **MAC aleatoria**; cuando se detecta, la app lo indica en vez de inventar un fabricante.
- Un smart TV, impresora o Chromecast suelen dar modelo exacto; una laptop o un celular con MAC aleatoria, mucho menos.

## Permisos

- `ACCESS_FINE_LOCATION`: obligatorio desde Android 8 para leer el nombre de la red (SSID). Se pide al abrir la app.
- `NEARBY_WIFI_DEVICES`: Android 13+ (incluido Android 16), para comunicarse con aparatos de la red local. Se pide al abrir la app.
- `POST_NOTIFICATIONS`: solo si activas **Vigilar mi red**, para avisarte de aparatos nuevos.

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
