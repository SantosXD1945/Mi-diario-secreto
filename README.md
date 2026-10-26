# Mi diario secreto

Diario de ánimo y actividades, solo para uso personal. Kotlin + Jetpack Compose.

## Qué incluye
- Registro de ánimo (5 niveles), actividades y nota. Se puede elegir otra fecha y hora.
- Editar y borrar entradas, historial y calendario por meses.
- Resumen: ánimo medio, racha, últimos 14 días y ánimo medio por actividad.
- Widget de registro rápido en la pantalla de inicio.
- Recordatorio diario (la notificación no muestra nada de tu diario).
- PIN de 4 a 6 números y desbloqueo con huella.
- Copia de seguridad cifrada (AES-256-GCM) en una carpeta que eliges. La app nunca borra copias anteriores.
- Exportar a CSV (sin cifrar).
- Tema oscuro (principal), claro o del sistema.
- Aviso de versiones nuevas.
- Los datos se quedan en el teléfono. Sin cuentas, sin servidor.

## Cómo se compila y se publica
Cada vez que subes cambios a `main`, GitHub compila la app (pestaña **Actions**, artefacto `diario-apk`).

Si en el repositorio están los secretos `KEYSTORE_B64` y `KEY_PASSWORD`, la app se firma siempre
con la misma llave y además se publica como **Release** (`v<número>`). Eso permite que la app
avise de versiones nuevas y que se instalen encima de la anterior sin perder datos.

**Guarda una copia de la llave de firma.** Si la pierdes, habrá que reinstalar la app desde cero
(haz una copia de seguridad antes).

## Pendiente
Metas, fotos y notas de voz.
