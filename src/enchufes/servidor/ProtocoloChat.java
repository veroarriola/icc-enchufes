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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static enchufes.cliente.Cliente.INI_COLOR_USUARIO;

/**
 * Esta clase atiende a cada cliente fungiendo como chat.
 * Los mesajes que envia el cliente son publicados en el servidor
 * y en los demás clientes conectados.
 * @author blackzafiro
 */
public class ProtocoloChat extends Thread {
	
	private final Servidor servidor;
	private String usuario;
	private final Socket enchufe;
	private PrintWriter out;
	private BufferedReader in;
	private boolean escuchando = true;
	
	public static final String CLAVE_USARIO_REGISTRADO = "[registrado]";
	
	/** Comando que debe enviar el cliente para terminar la conexión. */
	public final static String COMANDO_SALIR = "/salir";
	
	/** Comando para mostrar usuarios en el chat. */
	private static final String COMANDO_LISTAR = "/lista";
	
	/** Tabla de comandos implementados en el servidor. */
	private static final HashMap<String, String> COMANDOS = new HashMap<>();
	static {
		COMANDOS.put(COMANDO_SALIR, "Desconecta del servidor.");
		COMANDOS.put(COMANDO_LISTAR, "Lista a los otros usuarios en el chat.");
	}
	
	/** Frase que usa el servidor para indicar al cliente que lo ha desconectado. */
	public final static String PALABRA_SALIDA = "[¡Adios!]";
	
	/**
	 * Constructor para el hilo que atenderá al cliente.
	 * @param s servidor
	 * @param enchufe conexión aceptaa
	 */
	public ProtocoloChat(Servidor s, Socket enchufe) {
		servidor = s;
		this.enchufe = enchufe;
	}
	
	/**
	 * Pide al cliente su nombre de usuario y lo registra en el chat cuando
	 * éste sea válido.
	 * @return Si logró registrar al usuario.
	 */
	private boolean registraUsuario(){
		// Solicita nombre de usuario único
		usuario = null;
		try {
			// Accede flujos
			out = new PrintWriter(enchufe.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(enchufe.getInputStream()));
			
			for(String comando : COMANDOS.keySet()) {
				out.format("\033[1;35m%s\t%s\033[0m%n", comando, COMANDOS.get(comando));
			}
		
			boolean usuarioInvalido = true;
			do {
				if (usuario == null) {
					out.println("Escriba su nombre de usuario, debe ser único");
				} else {
					out.format("%s ya está ocupado, elige otro nombre de usuario.%n", usuario);
				}
				usuario = in.readLine().trim();
				
				if (usuario == null) {
					// El enchufe cliente no mandó su usuario
					System.err.println("Un cliente no envió su usuario.");
					enchufe.close();
					return false;
				} else if(usuario.equals("")) {
					out.println("Envió una cadena vacía.");
					usuario = null;
					continue;
				} else if(usuario.equals(COMANDO_SALIR)) {
					System.out.format("Cliente anónimo entró y salió%n");
					out.println(ProtocoloChat.PALABRA_SALIDA);
					enchufe.close();
					return false;
				} else if(COMANDOS.containsKey(usuario)) {
					out.println("Su nombre es un comando, elija otro.");
					usuario = null;
					continue;
				}
				
				System.out.format("%nRegistrando al usuario \033[1;96m %s \033[0m...", usuario);
				synchronized(this) {
					if(!servidor.clientes.containsKey(usuario)){
						servidor.clientes.put(usuario, this);
						servidor.clientesAnónimos.remove(this.hashCode());
						usuarioInvalido = false;
					}
				}
				System.out.format("%s registrado%n", usuario);
				
			} while(usuarioInvalido);
			
			out.format("%s %s%n", CLAVE_USARIO_REGISTRADO, usuario);
			out.format("%s \033[96m%s\033[0m bienvenid@ al chat.%n", Servidor.NOMBRE_SERVIDOR, usuario);
			servidor.notifica("[" + INI_COLOR_USUARIO + usuario +
					          "\033[0m ha ingresado al chat.]",
					          usuario);
			return true;
		}
		catch(java.net.SocketException ex) {
			Logger.getLogger(ProtocoloChat.class.getName()).log(Level.INFO, "El servidor cerr\u00f3 la conexi\u00f3n para {0}.", usuario);
			return false;
		}
		catch(IOException ex) {
			Logger.getLogger(ProtocoloChat.class.getName()).log(Level.SEVERE, null, ex);
			return false;
		}
		catch(NullPointerException ex) {
			Logger.getLogger(ProtocoloChat.class.getName()).log(Level.INFO, "Otro tipo de cliente hizo una prueba.", ex);
			return false;
		}
	}
	
	/**
	 * Registra al usuario, luego escucha y transmite sus mensajes al chat.
	 */
	@Override
	public void run() {
				
		if(!registraUsuario()) return;
				
		try {
			// Escucha y transmite
			String inputLine;
			while (escuchando && (inputLine = in.readLine()) != null) {
				switch(inputLine) {
					case COMANDO_SALIR:
						servidor.desconectaCliente(usuario);
						continue;
					case COMANDO_LISTAR:
						out.format("%s Inicia lista de usuarios%n", Servidor.NOMBRE_SERVIDOR);
						for(String usuario : servidor.clientes.keySet()) {
							out.format("\033[96m%s\033[0m%n", usuario);
						}
						out.format("%s Termina lista de usuarios%n", Servidor.NOMBRE_SERVIDOR);
						continue;
				}
				servidor.difundeMensaje(usuario, inputLine);
			}
			
			// Para deconexiones forzadas (Ej: si el cliente presiona Ctrl-C)
			if (servidor.clientesAnónimos.containsKey(this.hashCode())) {
				servidor.clientesAnónimos.remove(this.hashCode());
			}
			if (servidor.clientes.containsKey(usuario)) {
				servidor.clientes.remove(usuario);
			}
			System.out.format("%n %s se ha desconectado.%n", usuario);
			servidor.notifica(INI_COLOR_USUARIO + usuario +
			                  "\033[0m se ha desconectado.",
					          usuario);
		} catch (IOException ex) {
			if(enchufe.isClosed()) return;  // Se puede ignorar.
			Logger.getLogger(ProtocoloChat.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			cierraConexión();
		}
	}
	
	/**
	 * Envía el mensaje al cliente.
	 * @param usuario Cliente que envió el mensaje.
	 * @param msj     Texto enviado.
	 */
	public void difundeMensaje(String usuario, String msj) {
		out.format("[[%s]]: %s%n", usuario, msj);
	}
	
	/**
	 * Cierra la conexión con este cliente, sus flujos y termina la ejecución del hilo.
	 */
	public void cierraConexión() {
		try {
			if (!enchufe.isClosed()) {
				escuchando = false;
				out.println(PALABRA_SALIDA);
				enchufe.close();
			}
		} catch (IOException ex) {
			Logger.getLogger(ProtocoloChat.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
