# icc-enchufes-demo
Demo básico sobre el uso de enchufes en una red local

Para compilar usa:
```
make compile
```
Necesitas utilizar dos programas, el servidor:
```
make run_server
```
y, mientras corra el servidor, crea uno o varios clientes:
```
make run_client
```
Puedes abrir varios clientes utilizando varias terminales.

Este ejemplo se podría ejecutar con ant, pero se eligió usar
un Makefile para que veas los comando de java con los cuales
se ejecutan estos programas.
