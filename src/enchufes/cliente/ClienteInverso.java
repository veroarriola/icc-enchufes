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
package enchufes.cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static enchufes.servidor.ProtocoloChat.CLAVE_USARIO_REGISTRADO;

/**
 * Programa cliente para conectarse con el servidor de chat.
 * @author blackzafiro
 */
public class ClienteInverso {
	
	private String usuario;
	private String delServidor;
    private String delUsuario;
	private boolean conexiónViva = true;
	private String eco = null;
	
	public static final String INI_COLOR_USUARIO = "\033[1;34m";
	
	/**
	 * Conecta al cliente al servidor en la dirección y pueto indicados.
	 * @param anfitrión Dirección ip del servidor.
	 * @param puerto Puerto donde escucha el servidor.
	 */
	public ClienteInverso(String anfitrión, int puerto) {
		// try-with-resources cierra las conexiones cuando termina la ejecución
		// del bloque.
		try (
            Socket enchufe = new Socket(anfitrión, puerto);
            PrintWriter out = new PrintWriter(enchufe.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(enchufe.getInputStream()));
        ) {
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			
			System.out.println("\033[1;35m*..............................................................................*");
			System.out.println("*...                                  Chat                                  ...*");
 
			// Escucha e imprime los mensajes del servidor.
			Thread escuchaCliente = new Thread() {
				@Override
				public void run() {
					try {
						// Escucha, envía e imprime los mensajes del usuario.
						while (conexiónViva && (delUsuario = stdIn.readLine()) != null) {
							// Si la conexión se cierra readLine() aún no se da cuenta.
							if(conexiónViva) {
								out.println(delUsuario);
								eco = delUsuario;
							}
						}
					} catch (IOException ex) {
						Logger.getLogger(ClienteInverso.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			};
			escuchaCliente.start();
			
			// Recibe nombre de usuario verificado
			while(!(delServidor = in.readLine()).startsWith(CLAVE_USARIO_REGISTRADO) ) {
				System.out.println(delServidor);
			}
			// Se recibió mensaje con nombre de usuario confirmado
			usuario = delServidor.substring(CLAVE_USARIO_REGISTRADO.length()).trim();
			System.out.format("Tu nombre de usuario \033[96m %s \033[0m ha sido confirmado.%n", usuario);

			String[] partes;
			while ((delServidor = in.readLine()) != null) { // Si el servidor muere ¡lo espera por siempre y este hilo no muere!
				if (delServidor.equals(enchufes.servidor.ProtocoloChat.PALABRA_SALIDA)) {
					System.out.println("\033[1;35mConexión terminada, presiona cualquier tecla.");
					System.out.println("*..............................................................................*\033[0m");
					conexiónViva = false;
					break;
				}

				if((partes = extraeUsuario(delServidor)) != null) {
					if(partes[0].equals(usuario)) {
						System.out.println("\033[96m Yo: \033[0m " + partes[1]);
					} else {
						System.out.format("%s%s\033[0m: %s%n", INI_COLOR_USUARIO, partes[0], partes[1]);
					}
				} else {
					System.out.println(delServidor);
				}
			}
			
        } catch (UnknownHostException e) {
            System.err.format("%s desconocido%n", anfitrión);
            System.exit(1);
        } catch (IOException e) {
            System.err.format("No se pudieron abrir los flujos a %s en el puerto %d.%n",
                anfitrión, puerto);
            System.exit(1);
        }
	}
	
	/**
	 * Auxiliar para extraer el nombre de usuario del mensaje enviado por el
	 * servidor.
	 * @param msj Cadena enviada por el servidor.
	 * @return Arreglo con el nombre en la primera posición y mensaje en la
	 *         segunda.
	 */
	private static String[] extraeUsuario(String msj) {
		int fin;
		String[] partes = null;
		if(msj.startsWith("[[") && (fin = msj.indexOf("]]:")) > 0) {
			partes = new String[2];
			partes[0] = msj.substring(2,fin);   // Usuario
			partes[1] = msj.substring(fin+4);   // Mensaje
		}
		return partes;
	}
	
	/**
	 * Entrada del programa
	 * @param args Dirección del servidor y puerto.
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
            System.err.println(
                "Uso: java enchufes.Cliente <host name> <port number>");
            System.exit(1);
        }
 
        String anfitrión = args[0];
        int puerto = Integer.parseInt(args[1]);
		new ClienteInverso(anfitrión, puerto);
	}
}
