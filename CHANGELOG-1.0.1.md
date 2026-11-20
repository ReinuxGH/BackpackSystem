# BackpackSystem 1.0.1

## Cambios
- Se eliminó el sonido `UI_BUTTON_CLICK` que se reproducía al abrir una mochila.
- La descripción de la mochila ahora muestra la cantidad de espacios ocupados y hasta tres tipos de objetos almacenados.
- La descripción se actualiza al abrir y cerrar la mochila.
- Se conserva el UUID individual y el almacenamiento persistente por mochila.

## Alertas conocidas
- Si un jugador elimina o destruye una mochila, el contenido asociado a su UUID puede quedar sin acceso. No se implementa recuperación automática en esta versión.
- La prueba con varios jugadores queda pendiente.
- La vista previa es texto en la descripción del objeto; no representa gráficamente los objetos dentro del icono.

## Pruebas requeridas antes de instalar
1. Compilar con Java 25 y Paper API 26.3.
2. Abrir una mochila con contenido y comprobar la descripción.
3. Guardar y cerrar; comprobar que la descripción se actualiza.
4. Salir y volver a entrar; verificar contenido.
5. Reiniciar el servidor y verificar contenido.
6. Probar una mejora conservando todos los objetos.
