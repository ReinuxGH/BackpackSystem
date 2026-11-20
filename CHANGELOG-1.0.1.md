# BackpackSystem 1.0.1

## Cambios
- Mochilas basadas en PAPER con modelo personalizado: no pueden colocarse como bloques.
- Vista previa nativa de los objetos mediante el componente CONTAINER de Paper; viaja con el objeto y otros jugadores también la ven.
- El inventario real sigue guardándose por UUID en backpacks.yml; la vista previa es una copia visual.
- La descripción personalizada queda reducida a una sola línea gris: "Creada por: nombre".
- El nombre del creador se conserva al mejorar la mochila y al actualizar su descripción.
- Se eliminó el sonido UI_BUTTON_CLICK al abrir la mochila.
- Cada mochila básica fabricada recibe un UUID individual.

## Compatibilidad y alertas
- Las mochilas antiguas que no tengan el dato de creador mostrarán "Desconocido" al actualizarse.
- La vista previa del componente CONTAINER depende de Paper 26.3 y del cliente Minecraft compatible.
- La prueba con varios jugadores queda pendiente.

## Pruebas requeridas antes de instalar
1. Compilar con Java 25 y Paper API 26.3.
2. Comprobar que el tooltip solo tenga la línea gris del creador y la vista previa nativa.
3. Abrir/cerrar y confirmar que la vista previa se actualiza.
4. Pasar una mochila a otro jugador y comprobar que ve los objetos.
5. Confirmar que clic derecho en un bloque no coloca nada.
6. Salir, volver a entrar y reiniciar el servidor; verificar contenido.
7. Probar una mejora conservando todos los objetos.
