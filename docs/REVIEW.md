# Revisión de motor y experiencia de uso — 2026-10-13

## Diagnóstico

El fragmento proporcionado por el usuario termina en `runner.py`, línea 16. En el código de partida esa línea lee obligatoriamente `/proc/<pid del padre>/stat`, antes de ejecutar yt-dlp. La lectura no tiene manejo de errores. Es un punto de fallo compatible con el reporte, aunque falta el traceback completo del teléfono para confirmar su excepción concreta.

No se atribuye este fallo a PO Tokens, al solver ni a YouTube sin evidencia. Un fallo del supervisor ocurre antes de la extracción.

## Correcciones incluidas

- Supervisor mediante el pipe de entrada creado por ProcessBuilder: el proceso vigilante detecta EOF cuando termina la app y mata el grupo del extractor. También detecta la muerte del supervisor. No consulta `/proc` para arrancar. yt-dlp y FFmpeg reciben `/dev/null` como entrada y no pueden consumir el pipe del vigilante.
- La cancelación termina el proceso aunque la lectura de salida esté bloqueada. Un nuevo análisis espera la limpieza del anterior antes de reutilizar su identificador.
- El análisis utiliza las cookies cifradas importadas, igual que las descargas. La copia temporal se elimina al terminar o cancelar.
- Diagnóstico completo censurado en memoria y botón para copiarlo desde el error. Un proceso que termine sin salida informa su código de salida.
- Cancelar una descarga espera al trabajo antes de limpiar los archivos temporales.
- Reanudar una descarga inicia las transferencias; eliminar un archivo lo oculta inmediatamente y ofrece deshacer durante diez segundos.
- La selección avanzada de un formato de vídeo sin audio solicita también audio. La selección de un formato de audio se respeta en el motor.

## Diseño

Jerarquía más clara: una entrada principal, selector vídeo/audio y acceso separado a calidad y formato. Programación y formatos técnicos se despliegan cuando se necesitan. Navegación de cuatro destinos con iconos propios y botones de anchura uniforme. Tarjetas con miniaturas recortadas, estados en español/inglés y progreso indeterminado real durante el análisis; se elimina el falso 40 % fijo.

Ajustes agrupados y desplegables, biblioteca con filtros, búsqueda y estados vacíos adecuados. Superficies oscuras neutras, tema claro y AMOLED, ocho acentos, fuente Manrope con ejes de peso explícitos, foco visible y semántica de interruptores/selección. Contenido limitado a 840 dp en ventanas anchas; esto no equivale a un diseño completo de doble panel para tabletas.

Los builds debug usan `con.themralex.aydlp.preview` y el nombre Aytdlp Preview. Se instalan junto a producción con datos independientes. Los builds release conservan su applicationId y la firma existente; no se reemplaza ni se crea una nueva identidad de producción.

## Verificación

Se ejecutaron seis pruebas Python locales: reanudación de fixture, interrupción durante merge de fixture, reconocimiento/rechazo del solver, arranque del supervisor con `/proc` denegado y eliminación de descendientes al cerrar el pipe propietario. Las dos últimas ejercitan el runner real. Las pruebas de transferencia usan medios simulados y no demuestran integridad de descargas reales.

CI añade una prueba instrumentada que cancela un extractor Python silencioso y comprueba que puede reutilizar su identificador. Las pruebas Compose recorren descarga, error, cola, biblioteca y ajustes; guardan capturas reales en oscuro y claro. Los resultados finales de compilación/emulador se registran en VALIDATION.md cuando terminan.

## Límites y prioridades siguientes

No hay evidencia nueva de descarga real de YouTube en un teléfono ARM64, de seis horas de transferencias, recuperación de URLs caducadas ni resistencia a restricciones del fabricante. Tampoco hay benchmarks para afirmar superioridad sobre Seal o YTDLnis. El propio documento VALIDATION.md enumera funciones del prompt original todavía pendientes; esta revisión no las presenta como implementadas.

La interfaz ExtractionEngine, Room, Compose y los argumentos sin shell son una base razonable para continuar. La prioridad siguiente es validar vídeo/audio reales y pausa/reanudación/merge en el teléfono; después, perfiles de rendimiento y las funciones pendientes. Cambiar todas las dependencias a la vez haría más difícil aislar el fallo actual, por lo que esta revisión conserva las versiones existentes.

## Fuentes técnicas

- Fuentes variables en Compose: https://developer.android.com/develop/ui/compose/text/fonts
- Procesos y pipes: https://docs.python.org/3/library/subprocess.html
- Política de aislamiento Android: https://android.googlesource.com/platform/system/sepolicy/+/android16-release/private/app.te
