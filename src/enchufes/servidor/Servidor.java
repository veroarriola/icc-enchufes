/*
 * Copyright (c) 2021, blackzafiro. All rights reserved.
 *
 * This software was designed with purely academic purposes.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package enchufes.servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Programa servidor, se encarga de recibir conexiones y retransimitir mensajes
 * entre sus clientes en forma de chat.
 * 
 * Para correrlo en internet es necesario configurar el router de la red
 * donde se corra este programa.
 * https://www.puntoflotante.net/CONFIGURACION-ROUTER-INFINITUM-SERVIDORES-BOLT-ESP8266.htm
 * Esto depende del router, pero aquí se
 * listan los pasos básicos:
 * - Buscar la dirección IP del módem router.
 * - Activar el port forwarding hacia el dispositivo servidor
 * @author blackzafiro
 */
public class Servidor {
	
	private static final String INI_COLOR = "\033[1;35m";
	
	static final String NOMBRE_SERVIDOR = "\033[1;31mServidor:\033[0m";
	
	/** Comando para teminar la ejecución del servidor. */
	private static final String COMANDO_SALIR = "/salir";
	
	/** Comando para mostrar usuarios en la tabla. */
	private static final String COMANDO_LISTAR = "/lista";
	
	/** Se encarga de escuchar por nuevas conexiones. */
	private ServerSocket servidor = null;
	
	/** Usuarios con conexión activa a este servidor. */
	ConcurrentHashMap<String, ProtocoloChat> clientes = new ConcurrentHashMap<>();
	
	/** Clientes cuyo protocolo de indentificación no ha sido completado. */
	ConcurrentHashMap<Integer, ProtocoloChat> clientesAnónimos = new ConcurrentHashMap<>();
	
	/**
	 * Crea un servidor en esta computadora en el puerto indicado.
	 * @param puerto 
	 */
	public Servidor(int puerto) {
		try {
			
			servidor = new ServerSocket(puerto);
			
			imprimeComandos();
			
			// Hilo encargado de escuchar comandos del usuario desde la consola.
			new Thread() {
				@Override
				public void run() {
					Scanner teclado = new Scanner(System.in);

					while (true) {
						String key = teclado.nextLine().toLowerCase();
						System.out.format("Echo: \033[1;35m%s\033[0m%n",
					           key);
						
						switch (key) {
							case COMANDO_LISTAR:
								if (clientes.isEmpty()) {
									System.out.println("No hay usuarios registrados.");
								} else {
									for(String usuario : clientes.keySet()) {
										System.out.println(usuario);
									}
								}
								break;
							case COMANDO_SALIR:
								Servidor.this.close();
								return;
						}
					}
				}
			}.start();
			
			// Escucha por nuevas conexiones y crea un hilo por enchufeCliente aceptado.
			recibeClientes();
			
		} catch(IOException ioe) {
			// No se pudo montar el servidor.
			System.err.println("No se pudo crear el enchufe.");
			System.err.println(ioe.toString());
			System.exit(-1);
		}
	}
	
	/** Imprime estado y comandos disponibles. */
	private void imprimeComandos() {
		System.out.format("%sServidor levantado en %s puerto %d\033[0m%n",
				  INI_COLOR,
				  servidor.getInetAddress(),
				  servidor.getLocalPort());
		System.out.format("%s  %s para terminar la ejecución.%n", INI_COLOR, COMANDO_SALIR);
		System.out.format("  %s para mostrar la tabla de usuarios.\033[0m%n", COMANDO_LISTAR);
	}
	
	/**
	 * Escucha permanentemente por nuevas conexiones y crea hilos para
	 * atender a los clientes nuevos.
	 */
	private void recibeClientes() {
		while(true) {
			try {
				// La siguiente llamada bloquea este hilo hasta que un
				// enchufeCliente se haya conectado.
				Socket enchufeCliente = servidor.accept();
				// Crear e iniciar hilo para atender cliente.
				ProtocoloChat clienteNuevo = new ProtocoloChat(this, enchufeCliente);
				clientesAnónimos.put(clienteNuevo.hashCode(), clienteNuevo);
				clienteNuevo.start();
			} catch (IOException e) {
				if(servidor.isClosed()) return;
				System.err.println("Fallo al aceptar cliente.");
				return;
			}
		}
	}
	
	/**
	 * Desconecta al enchufeCliente y lo quita de la tabla de clientes.
	 * @param usuario 
	 */
	void desconectaCliente(String usuario) {
		clientes.get(usuario).cierraConexión();
		clientes.remove(usuario);
	}
	
	/**
	 * Utilizado para que el servidor envíe notificaciones a los clientes,
	 * puede no notificar a un cliente en particular.
	 * @param msj Notificación.
	 * @param excepto Usuario que no necesita el mensaje (opcional).
	 */
	void notifica(String msj, String excepto) {
		System.out.format("   %s %s%n", NOMBRE_SERVIDOR, msj);
		if(excepto != null) {
			for(String usuario : clientes.keySet()) {
				if (!excepto.equals(usuario)) {
					clientes.get(usuario).difundeMensaje(NOMBRE_SERVIDOR, msj);
				}
			}
		} else {
			for(String usuario : clientes.keySet()) {
				clientes.get(usuario).difundeMensaje(NOMBRE_SERVIDOR, msj);
			}
		}
	}
	
	/**
	 * Transmite el mensaje enviado por usuario a todos los demás clientes
	 * registrados en el chat.
	 * @param usuario
	 * @param msj 
	 */
	public void difundeMensaje(String usuario, String msj) {
		System.out.format("   \033[34m %s \033[0m: %s%n", usuario, msj);
		for(String colega : clientes.keySet()) {
			//if (!usuario.equals(colega)) {
				clientes.get(colega).difundeMensaje(usuario, msj);
			//}
		}
	}
	
	/**
	 * Este método puede ser llamado desde otro hilo para apagar el servidor.
	 */
	public void close() {
		
		clientesAnónimos.keySet().forEach(usuario -> {
			clientesAnónimos.get(usuario).cierraConexión();
		});
		clientes.keySet().forEach(usuario -> {
			clientes.get(usuario).cierraConexión();
		});
		try {
			if (!servidor.isClosed()) {
				servidor.close();
				System.out.println("Servidor cerrado satisfactoriamente.");
			}
		} catch(IOException ioe) {
			// No se cerrar el enchufe servidor.
			System.err.println("No se pudo cerrar el enchufe.");
			System.err.println(ioe.toString());
		}
		
	}
	
	/**
	 * Levanta un servidor en localhost: 1234 o
	 * en la dirección y puerto indicados.
	 * @param args 
	 */
	public static void main(String[] args) {
		int PUERTO_POR_DEFECTO = 1557;
		int puerto = PUERTO_POR_DEFECTO;
		
		if (args.length == 1) {
			try {
				puerto = Integer.parseInt(args[0]);
			} catch (NumberFormatException ex) {
				System.out.println("Uso: java enchufes.Servidor [<puerto>]");
				System.exit(-1);
			}
		}
		
		new Servidor(puerto);
	}
}
