# BackpackSystem 1.0.1

## Cambios
- Se eliminó el sonido `UI_BUTTON_CLICK` que se reproducía al abrir una mochila.
- El objeto base pasa a ser una caja de shulker para aprovechar la vista previa nativa de objetos de Minecraft.
- La vista previa gráfica nativa muestra hasta 27 espacios; las mochilas de 36 y 45 espacios conservan su contenido completo en el almacenamiento del plugin, aunque la vista previa solo refleja los primeros 27.
- La descripción se actualiza al abrir y cerrar la mochila.
- Cada mochila básica fabricada recibe un UUID individual, evitando que varias mochilas compartan el mismo contenido.
- Se conserva el UUID individual y el almacenamiento persistente por mochila.

## Alertas conocidas
- Si un jugador elimina o destruye una mochila, el contenido asociado a su UUID puede quedar sin acceso. No se implementa recuperación automática en esta versión.
- La prueba con varios jugadores queda pendiente.
- Las mochilas de versiones anteriores se convierten al nuevo objeto cuando se abren; se conserva su UUID y el contenido guardado por el plugin.

## Pruebas requeridas antes de instalar
1. Compilar con Java 25 y Paper API 26.3.
2. Abrir una mochila con contenido y comprobar la descripción.
3. Guardar y cerrar; comprobar que la descripción se actualiza.
4. Salir y volver a entrar; verificar contenido.
5. Reiniciar el servidor y verificar contenido.
6. Probar una mejora conservando todos los objetos.
