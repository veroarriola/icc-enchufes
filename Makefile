compile:
	javac -d ./build --source-path ./src src/enchufes/servidor/*.java
	javac -d ./build --source-path ./src src/enchufes/cliente/*.java

run_server:
	java -classpath build enchufes.servidor.Servidor
	
run_client:
	java -classpath build enchufes.cliente.Cliente 0.0.0.0 1557

.PHONY: clean
clean:
	rm -rf build
